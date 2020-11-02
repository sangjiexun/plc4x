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
    "encoding/base64"
    "encoding/xml"
    "errors"
    "io"
    "plc4x.apache.org/plc4go-modbus-driver/v0/internal/plc4go/spi"
    "plc4x.apache.org/plc4go-modbus-driver/v0/internal/plc4go/utils"
)

// The data-structure of this message
type IPAddress struct {
    Addr []int8

}

// The corresponding interface
type IIPAddress interface {
    spi.Message
    Serialize(io utils.WriteBuffer) error
}


func NewIPAddress(addr []int8) spi.Message {
    return &IPAddress{Addr: addr}
}

func CastIIPAddress(structType interface{}) IIPAddress {
    castFunc := func(typ interface{}) IIPAddress {
        if iIPAddress, ok := typ.(IIPAddress); ok {
            return iIPAddress
        }
        return nil
    }
    return castFunc(structType)
}

func CastIPAddress(structType interface{}) IPAddress {
    castFunc := func(typ interface{}) IPAddress {
        if sIPAddress, ok := typ.(IPAddress); ok {
            return sIPAddress
        }
        if sIPAddress, ok := typ.(*IPAddress); ok {
            return *sIPAddress
        }
        return IPAddress{}
    }
    return castFunc(structType)
}

func (m IPAddress) LengthInBits() uint16 {
    var lengthInBits uint16 = 0

    // Array field
    if len(m.Addr) > 0 {
        lengthInBits += 8 * uint16(len(m.Addr))
    }

    return lengthInBits
}

func (m IPAddress) LengthInBytes() uint16 {
    return m.LengthInBits() / 8
}

func IPAddressParse(io *utils.ReadBuffer) (spi.Message, error) {

    // Array field (addr)
    // Count array
    addr := make([]int8, uint16(4))
    for curItem := uint16(0); curItem < uint16(uint16(4)); curItem++ {

        _item, _err := io.ReadInt8(8)
        if _err != nil {
            return nil, errors.New("Error parsing 'addr' field " + _err.Error())
        }
        addr[curItem] = _item
    }

    // Create the instance
    return NewIPAddress(addr), nil
}

func (m IPAddress) Serialize(io utils.WriteBuffer) error {

    // Array Field (addr)
    if m.Addr != nil {
        for _, _element := range m.Addr {
            _elementErr := io.WriteInt8(8, _element)
            if _elementErr != nil {
                return errors.New("Error serializing 'addr' field " + _elementErr.Error())
            }
        }
    }

    return nil
}

func (m *IPAddress) UnmarshalXML(d *xml.Decoder, start xml.StartElement) error {
    for {
        token, err := d.Token()
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
            case "addr":
                var _encoded string
                if err := d.DecodeElement(&_encoded, &tok); err != nil {
                    return err
                }
                _decoded := make([]byte, base64.StdEncoding.DecodedLen(len(_encoded)))
                _len, err := base64.StdEncoding.Decode(_decoded, []byte(_encoded))
                if err != nil {
                    return err
                }
                m.Addr = utils.ByteToInt8(_decoded[0:_len])
            }
        }
    }
}

func (m IPAddress) MarshalXML(e *xml.Encoder, start xml.StartElement) error {
    if err := e.EncodeToken(xml.StartElement{Name: start.Name, Attr: []xml.Attr{
            {Name: xml.Name{Local: "className"}, Value: "org.apache.plc4x.java.knxnetip.readwrite.IPAddress"},
        }}); err != nil {
        return err
    }
    _encodedAddr := make([]byte, base64.StdEncoding.EncodedLen(len(m.Addr)))
    base64.StdEncoding.Encode(_encodedAddr, utils.Int8ToByte(m.Addr))
    if err := e.EncodeElement(_encodedAddr, xml.StartElement{Name: xml.Name{Local: "addr"}}); err != nil {
        return err
    }
    if err := e.EncodeToken(xml.EndElement{Name: start.Name}); err != nil {
        return err
    }
    return nil
}
