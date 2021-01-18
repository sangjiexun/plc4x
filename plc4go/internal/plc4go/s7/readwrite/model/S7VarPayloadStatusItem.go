//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//
package model

import (
    "encoding/xml"
    "errors"
    "github.com/apache/plc4x/plc4go/internal/plc4go/spi/utils"
    "io"
)

// The data-structure of this message
type S7VarPayloadStatusItem struct {
    ReturnCode DataTransportErrorCode
    IS7VarPayloadStatusItem
}

// The corresponding interface
type IS7VarPayloadStatusItem interface {
    LengthInBytes() uint16
    LengthInBits() uint16
    Serialize(io utils.WriteBuffer) error
    xml.Marshaler
}

func NewS7VarPayloadStatusItem(returnCode DataTransportErrorCode) *S7VarPayloadStatusItem {
    return &S7VarPayloadStatusItem{ReturnCode: returnCode}
}

func CastS7VarPayloadStatusItem(structType interface{}) *S7VarPayloadStatusItem {
    castFunc := func(typ interface{}) *S7VarPayloadStatusItem {
        if casted, ok := typ.(S7VarPayloadStatusItem); ok {
            return &casted
        }
        if casted, ok := typ.(*S7VarPayloadStatusItem); ok {
            return casted
        }
        return nil
    }
    return castFunc(structType)
}

func (m *S7VarPayloadStatusItem) GetTypeName() string {
    return "S7VarPayloadStatusItem"
}

func (m *S7VarPayloadStatusItem) LengthInBits() uint16 {
    lengthInBits := uint16(0)

    // Enum Field (returnCode)
    lengthInBits += 8

    return lengthInBits
}

func (m *S7VarPayloadStatusItem) LengthInBytes() uint16 {
    return m.LengthInBits() / 8
}

func S7VarPayloadStatusItemParse(io *utils.ReadBuffer) (*S7VarPayloadStatusItem, error) {

    // Enum field (returnCode)
    returnCode, _returnCodeErr := DataTransportErrorCodeParse(io)
    if _returnCodeErr != nil {
        return nil, errors.New("Error parsing 'returnCode' field " + _returnCodeErr.Error())
    }

    // Create the instance
    return NewS7VarPayloadStatusItem(returnCode), nil
}

func (m *S7VarPayloadStatusItem) Serialize(io utils.WriteBuffer) error {

    // Enum field (returnCode)
    returnCode := CastDataTransportErrorCode(m.ReturnCode)
    _returnCodeErr := returnCode.Serialize(io)
    if _returnCodeErr != nil {
        return errors.New("Error serializing 'returnCode' field " + _returnCodeErr.Error())
    }

    return nil
}

func (m *S7VarPayloadStatusItem) UnmarshalXML(d *xml.Decoder, start xml.StartElement) error {
    var token xml.Token
    var err error
    for {
        token, err = d.Token()
        if err != nil {
            if err == io.EOF {
                return nil
            }
            return err
        }
        switch token.(type) {
        case xml.StartElement:
            tok := token.(xml.StartElement)
            switch tok.Name.Local {
            case "returnCode":
                var data DataTransportErrorCode
                if err := d.DecodeElement(&data, &tok); err != nil {
                    return err
                }
                m.ReturnCode = data
            }
        }
    }
}

func (m *S7VarPayloadStatusItem) MarshalXML(e *xml.Encoder, start xml.StartElement) error {
    className := "org.apache.plc4x.java.s7.readwrite.S7VarPayloadStatusItem"
    if err := e.EncodeToken(xml.StartElement{Name: start.Name, Attr: []xml.Attr{
            {Name: xml.Name{Local: "className"}, Value: className},
        }}); err != nil {
        return err
    }
    if err := e.EncodeElement(m.ReturnCode, xml.StartElement{Name: xml.Name{Local: "returnCode"}}); err != nil {
        return err
    }
    if err := e.EncodeToken(xml.EndElement{Name: start.Name}); err != nil {
        return err
    }
    return nil
}

