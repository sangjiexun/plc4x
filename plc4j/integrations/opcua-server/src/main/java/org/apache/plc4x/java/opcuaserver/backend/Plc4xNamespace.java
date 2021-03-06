/*
 * Copyright (c) 2019 the Eclipse Milo Authors
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.apache.plc4x.java.opcuaserver.backend;

import org.apache.plc4x.java.opcuaserver.*;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.apache.plc4x.java.opcuaserver.configuration.Configuration;
import org.apache.plc4x.java.opcuaserver.configuration.DeviceConfiguration;
import org.apache.plc4x.java.opcuaserver.configuration.Tag;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.ValueRank;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.Lifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.DataTypeDictionaryManager;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.BaseEventTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.ServerTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.nodes.variables.AnalogItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.factories.NodeFactory;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilters;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.BuiltinDataType;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.XmlElement;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.StructureType;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumField;
import org.eclipse.milo.opcua.stack.core.types.structured.Range;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.plc4x.java.api.model.PlcField;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

public class Plc4xNamespace extends ManagedNamespaceWithLifecycle {

    public static final String NAMESPACE_URI = "urn:eclipse:milo:plc4x:server";

    private Configuration config;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private volatile Thread eventThread;
    private volatile boolean keepPostingEvents = true;

    private final Random random = new Random();

    private final DataTypeDictionaryManager dictionaryManager;

    private final SubscriptionModel subscriptionModel;

    private Plc4xCommunication plc4xServer;

    public Plc4xNamespace(OpcUaServer server, Configuration c) {
        super(server, NAMESPACE_URI);

        this.config = c;
        subscriptionModel = new SubscriptionModel(server, this);
        dictionaryManager = new DataTypeDictionaryManager(getNodeContext(), NAMESPACE_URI);

        plc4xServer = new Plc4xCommunication();

        getLifecycleManager().addLifecycle(dictionaryManager);
        getLifecycleManager().addLifecycle(subscriptionModel);

        getLifecycleManager().addStartupTask(this::addNodes);
    }

    private void addNodes() {
        for (DeviceConfiguration c: config.getDevices()) {
            createAndAddNodes(c);
        }
    }

    private void createAndAddNodes(DeviceConfiguration c) {

        NodeId folderNodeId = newNodeId(c.getName());

        UaFolderNode folderNode = new UaFolderNode(
            getNodeContext(),
            folderNodeId,
            newQualifiedName(c.getName()),
            LocalizedText.english(c.getName())
        );

        getNodeManager().addNode(folderNode);

        // Make sure our new folder shows up under the server's Objects folder.
        folderNode.addReference(new Reference(
            folderNode.getNodeId(),
            Identifiers.Organizes,
            Identifiers.ObjectsFolder.expanded(),
            false
        ));

        addDynamicNodes(folderNode, c);
    }

    private void addDynamicNodes(UaFolderNode rootNode, DeviceConfiguration c) {
        final List<Tag> tags = c.getTags();
        final String connectionString = c.getConnectionString();
        for (int i = 0; i < tags.size(); i++) {
            logger.info("Adding Tag " + tags.get(i).getAlias() + " - " + tags.get(i).getAddress());
            String name = tags.get(i).getAlias();
            final String tag = tags.get(i).getAddress();

            Class datatype = null;
            NodeId typeId = Identifiers.String;
            UaVariableNode node = null;
            Variant variant = null;
            try {
                datatype = plc4xServer.getField(tag, connectionString).getDefaultJavaType();
                final int length = plc4xServer.getField(tag, connectionString).getNumberOfElements();
                typeId = Plc4xCommunication.getNodeId(plc4xServer.getField(tag, connectionString).getPlcDataType());


                if (length > 1) {
                    node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                        .setNodeId(newNodeId(name))
                        .setAccessLevel(AccessLevel.READ_WRITE)
                        .setUserAccessLevel(AccessLevel.READ_WRITE)
                        .setBrowseName(newQualifiedName(name))
                        .setDisplayName(LocalizedText.english(name))
                        .setDataType(typeId)
                        .setTypeDefinition(Identifiers.BaseDataVariableType)
                        .setValueRank(ValueRank.OneDimension.getValue())
                        .setArrayDimensions(new UInteger[]{uint(length)})
                        .build();

                    Object array = Array.newInstance(datatype, length);
                    for (int j = 0; j < length; j++) {
                        Array.set(array, j, false);
                    }
                    variant = new Variant(array);
                } else {
                    node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                        .setNodeId(newNodeId(name))
                        .setAccessLevel(AccessLevel.READ_WRITE)
                        .setUserAccessLevel(AccessLevel.READ_WRITE)
                        .setBrowseName(newQualifiedName(name))
                        .setDisplayName(LocalizedText.english(name))
                        .setDataType(typeId)
                        .setTypeDefinition(Identifiers.BaseDataVariableType)
                        .build();
                    variant = new Variant(0);
                }

                node.setValue(new DataValue(variant));

                node.getFilterChain().addLast(
                    AttributeFilters.getValue(
                        ctx -> plc4xServer.getValue(ctx, tag, connectionString)
                    )
                );

                node.getFilterChain().addLast(
                    AttributeFilters.setValue(
                        (ctx, value) -> {
                            if (length > 1) {
                                plc4xServer.setValue(tag, Arrays.toString((Object[]) value.getValue().getValue()), connectionString);
                            } else {
                                plc4xServer.setValue(tag, value.getValue().getValue().toString(), connectionString);
                            }

                        }
                    )
                );

            } catch (PlcConnectionException e) {
                logger.info("Couldn't find data type");
                System.exit(1);
            }

            getNodeManager().addNode(node);
            rootNode.addOrganizes(node);
        }
    }


    @Override
    public void onDataItemsCreated(List<DataItem> dataItems) {
        for (DataItem item : dataItems) {
            plc4xServer.addField(item);
        }

        subscriptionModel.onDataItemsCreated(dataItems);
    }

    @Override
    public void onDataItemsModified(List<DataItem> dataItems) {
        for (DataItem item : dataItems) {
            plc4xServer.addField(item);
        }
        subscriptionModel.onDataItemsModified(dataItems);
    }

    @Override
    public void onDataItemsDeleted(List<DataItem> dataItems) {
        for (DataItem item : dataItems) {
            plc4xServer.removeField(item);
        }
        subscriptionModel.onDataItemsDeleted(dataItems);
    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        logger.info(" 4 - " + monitoredItems.toString());
        for (MonitoredItem item : monitoredItems) {
            logger.info(" 4 - " + item.toString());
        }
        subscriptionModel.onMonitoringModeChanged(monitoredItems);
    }

}
