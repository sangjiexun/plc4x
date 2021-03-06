/*
 * Copyright (c) 2019 the Eclipse Milo Authors
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.apache.plc4x.java.opcuaserver;

import java.io.File;
import java.security.KeyPair;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import java.io.IOException;
import java.io.Console;
import java.nio.file.Path;
import java.nio.file.FileSystems;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.CompositeValidator;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.X509IdentityValidator;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaRuntimeException;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedHttpsCertificateBuilder;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;
import org.eclipse.milo.opcua.stack.server.security.DefaultServerCertificateValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.collect.Lists.newArrayList;
import org.apache.commons.lang3.RandomStringUtils;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_X509;

import org.apache.commons.cli.*;

import org.apache.plc4x.java.opcuaserver.backend.Plc4xNamespace;
import org.apache.plc4x.java.opcuaserver.configuration.*;

public class OPCUAServer {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Configuration config;
    private PasswordConfiguration passwordConfig;
    private CommandLine cmd = null;

    static {
        // Required for SecurityPolicy.Aes256_Sha256_RsaPss
        Security.addProvider(new BouncyCastleProvider());
    }

    protected String[] setPasswords() {
        Console cnsl = System.console();
        String[] ret = new String[3];

        System.out.println("Please enter password for certificate:- ");
        ret[0] = String.valueOf(cnsl.readPassword());

        System.out.println("Please enter a username for the OPC UA server admin account:- ");
        ret[1] = String.valueOf(cnsl.readLine());

        System.out.println("Please enter a password for the OPC UA server admin account:- ");
        ret[2] = String.valueOf(cnsl.readPassword());

        return ret;
    }

    private void setPasswordWrapper() {
        String[] ret;
        if (cmd.hasOption("test")) {
            ret = new String[] {"password", "admin", "password"};
        } else {
            ret = setPasswords();
        }
        try {
            passwordConfig.setSecurityPassword(ret[0]);
            passwordConfig.createUser(ret[1], ret[2], "admin-group");
        } catch (IOException e) {
            logger.error("Unable to save config file, please check folder permissions. " + e);
            System.exit(1);
        }
    }

    private void readPasswordConfig() {
        //Read Config File
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();
        try {
            Path path = FileSystems.getDefault().getPath(config.getDir()).resolve("security/.jibberish");
            File file = path.toFile();
            if (file.isFile() && !cmd.hasOption("set-passwords")) {
                passwordConfig = mapper.readValue(file, PasswordConfiguration.class);
                passwordConfig.setPasswordConfigFile(path);
            } else if (file.isFile() && cmd.hasOption("set-passwords")) {
                passwordConfig = mapper.readValue(file, PasswordConfiguration.class);
                passwordConfig.setPasswordConfigFile(path);
                setPasswordWrapper();
            } else {
                if (cmd.hasOption("interactive") || cmd.hasOption("set-passwords")) {
                    file.getParentFile().mkdirs();
                    passwordConfig = new PasswordConfiguration();
                    passwordConfig.setVersion("0.8");
                    passwordConfig.setPasswordConfigFile(path);
                    setPasswordWrapper();
                } else {
                    logger.info("Please re-run with the -i switch to setup the config file");
                    System.exit(1);
                }
            }
        } catch (IOException e) {
            logger.info("Error parsing password file " + e);
        }
    }

    private void readCommandLineArgs(String[] args) {
        Options options = new Options();

        Option input = new Option("c", "configfile", true, "configuration file");
        input.setRequired(true);
        options.addOption(input);

        Option setPassword = new Option("s", "set-passwords", false, "Reset passwords");
        setPassword.setRequired(false);
        options.addOption(setPassword);

        Option interactive = new Option("i", "interactive", false, "Interactively get asked to setup the config file from the console");
        interactive.setRequired(false);
        options.addOption(interactive);

        Option test = new Option("t", "test", false, "Used for testing the OPC UA Server");
        test.setRequired(false);
        options.addOption(test);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.info(e.getMessage());
            formatter.printHelp("Plc4x OPC UA Server", options);
            System.exit(1);
        }

        String configFile = cmd.getOptionValue("configfile");
        logger.info("Reading configuration file: {}", configFile);

        //Read Config File
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();
        try {
            config = mapper.readValue(new File(configFile), Configuration.class);
            config.setConfigFile(configFile);
            //Checking if the security directory has been configured.
            if (config.getDir() == null) {
                throw new IOException("Please set the dir in the config file");
            }

            readPasswordConfig();
        } catch (IOException e) {
            logger.info("Error parsing config file " + e);
            System.exit(1);
        }
    }

    public static void main(String[] args) throws Exception {
        OPCUAServer server = new OPCUAServer(args);
        server.startup().get();
        final CompletableFuture<Void> future = new CompletableFuture<>();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> future.complete(null)));
        future.get();
    }

    private final OpcUaServer server;
    private final Plc4xNamespace plc4xNamespace;

    public OPCUAServer(String[] args) throws Exception {

        readCommandLineArgs(args);

        KeyStoreLoader loader = new KeyStoreLoader(config, passwordConfig, cmd.hasOption("interactive"));

        DefaultCertificateManager certificateManager = new DefaultCertificateManager(
            loader.getServerKeyPair(),
            loader.getServerCertificateChain()
        );

        File pkiDir = FileSystems.getDefault().getPath(config.getDir()).resolve("pki").toFile();
        DefaultTrustListManager trustListManager = new DefaultTrustListManager(pkiDir);
        logger.info("pki dir: {}", pkiDir.getAbsolutePath());

        DefaultServerCertificateValidator certificateValidator =
            new DefaultServerCertificateValidator(trustListManager);

        KeyPair httpsKeyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);

        SelfSignedHttpsCertificateBuilder httpsCertificateBuilder = new SelfSignedHttpsCertificateBuilder(httpsKeyPair);
        httpsCertificateBuilder.setCommonName(HostnameUtil.getHostname());
        HostnameUtil.getHostnames("0.0.0.0").forEach(httpsCertificateBuilder::addDnsName);
        X509Certificate httpsCertificate = httpsCertificateBuilder.build();

        UsernameIdentityValidator identityValidator = new UsernameIdentityValidator(
            true,
            authChallenge -> {
                boolean check = passwordConfig.checkPassword(authChallenge.getUsername(), authChallenge.getPassword());
                if (!check) {
                    logger.info("Invalid password for user:- " + authChallenge.getUsername());
                }
                return check;
            }
        );

        X509IdentityValidator x509IdentityValidator = new X509IdentityValidator(c -> true);

        // If you need to use multiple certificates you'll have to be smarter than this.
        X509Certificate certificate = certificateManager.getCertificates()
            .stream()
            .findFirst()
            .orElseThrow(() -> new UaRuntimeException(StatusCodes.Bad_ConfigurationError, "no certificate found"));

        // The configured application URI must match the one in the certificate(s)
        String applicationUri = CertificateUtil
            .getSanUri(certificate)
            .orElseThrow(() -> new UaRuntimeException(
                StatusCodes.Bad_ConfigurationError,
                "certificate is missing the application URI"));

        Set<EndpointConfiguration> endpointConfigurations = createEndpointConfigurations(certificate);

        OpcUaServerConfig serverConfig = OpcUaServerConfig.builder()
            .setApplicationUri(applicationUri)
            .setApplicationName(LocalizedText.english(applicationUri))
            .setEndpoints(endpointConfigurations)
            .setBuildInfo(
                new BuildInfo(
                    "urn:eclipse:milo:plc4x:server",
                    "org.apache.plc4x",
                    config.getName(),
                    OpcUaServer.SDK_VERSION,
                    "", DateTime.now()))
            .setCertificateManager(certificateManager)
            .setTrustListManager(trustListManager)
            .setCertificateValidator(certificateValidator)
            .setHttpsKeyPair(httpsKeyPair)
            .setHttpsCertificate(httpsCertificate)
            .setIdentityValidator(new CompositeValidator(identityValidator, x509IdentityValidator))
            .setProductUri("urn:eclipse:milo:plc4x:server")
            .build();

        server = new OpcUaServer(serverConfig);

        plc4xNamespace = new Plc4xNamespace(server, config);
        plc4xNamespace.startup();
    }

    private Set<EndpointConfiguration> createEndpointConfigurations(X509Certificate certificate) {
        Set<EndpointConfiguration> endpointConfigurations = new LinkedHashSet<>();

        List<String> bindAddresses = newArrayList();
        bindAddresses.add("0.0.0.0");

        List<String> localAddresses = new ArrayList<>(bindAddresses);

        Set<String> hostnames = new LinkedHashSet<>();
        hostnames.add(HostnameUtil.getHostname());
        hostnames.addAll(HostnameUtil.getHostnames("0.0.0.0"));

        for (String bindAddress : bindAddresses) {
            for (String hostname : hostnames) {
                EndpointConfiguration.Builder builder = EndpointConfiguration.newBuilder()
                    .setBindAddress(bindAddress)
                    .setHostname(hostname)
                    .setPath("/plc4x")
                    .setCertificate(certificate)
                    .addTokenPolicies(
                        USER_TOKEN_POLICY_ANONYMOUS,
                        USER_TOKEN_POLICY_USERNAME,
                        USER_TOKEN_POLICY_X509);


                if (!config.getDisableInsecureEndpoint()) {
                    EndpointConfiguration.Builder noSecurityBuilder = builder.copy()
                        .setSecurityPolicy(SecurityPolicy.None)
                        .setSecurityMode(MessageSecurityMode.None);
                        endpointConfigurations.add(buildTcpEndpoint(noSecurityBuilder));
                        endpointConfigurations.add(buildHttpsEndpoint(noSecurityBuilder));
                } else {
                    //Always add an unsecured endpoint to localhost, this is a work around for Milo throughing an exception if it isn't here.
                    if (hostname.equals("127.0.0.1")) {
                        EndpointConfiguration.Builder noSecurityBuilder = builder.copy()
                            .setSecurityPolicy(SecurityPolicy.None)
                            .setSecurityMode(MessageSecurityMode.None);
                            endpointConfigurations.add(buildTcpEndpoint(noSecurityBuilder));
                            endpointConfigurations.add(buildHttpsEndpoint(noSecurityBuilder));
                    }
                }

                // TCP Basic256Sha256 / SignAndEncrypt
                endpointConfigurations.add(buildTcpEndpoint(
                    builder.copy()
                        .setSecurityPolicy(SecurityPolicy.Basic256Sha256)
                        .setSecurityMode(MessageSecurityMode.SignAndEncrypt))
                );

                // HTTPS Basic256Sha256 / Sign (SignAndEncrypt not allowed for HTTPS)
                endpointConfigurations.add(buildHttpsEndpoint(
                    builder.copy()
                        .setSecurityPolicy(SecurityPolicy.Basic256Sha256)
                        .setSecurityMode(MessageSecurityMode.Sign))
                );

                EndpointConfiguration.Builder discoveryBuilder = builder.copy()
                    .setPath("/discovery")
                    .setSecurityPolicy(SecurityPolicy.None)
                    .setSecurityMode(MessageSecurityMode.None);


                endpointConfigurations.add(buildTcpEndpoint(discoveryBuilder));
                endpointConfigurations.add(buildHttpsEndpoint(discoveryBuilder));
            }
        }

        return endpointConfigurations;
    }

    private EndpointConfiguration buildTcpEndpoint(EndpointConfiguration.Builder base) {
        return base.copy()
            .setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
            .setBindPort(config.getTcpPort())
            .build();
    }

    private EndpointConfiguration buildHttpsEndpoint(EndpointConfiguration.Builder base) {
        return base.copy()
            .setTransportProfile(TransportProfile.HTTPS_UABINARY)
            .setBindPort(config.getHttpPort())
            .build();
    }

    public OpcUaServer getServer() {
        return server;
    }

    public CompletableFuture<OpcUaServer> startup() {
        return server.startup();
    }

    public CompletableFuture<OpcUaServer> shutdown() {
        plc4xNamespace.shutdown();

        return server.shutdown();
    }

}
