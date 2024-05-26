package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
//TODO: Implement here the TFTP encoder and decoder
private byte[] bytes = new byte[1 << 10]; //start with 1k
private int len = 0;

private String MsgType;
private byte indecator = -1;

private short packetSize;


public String defineMsg (byte [] defineOPC) {
    short opCode= ByteBuffer.wrap(defineOPC).getShort();
     this.MsgType = "Illegal TFTP operation - Unknown Opcode.";

    if (opCode == 1) {
        this.MsgType = "RRQ";
        indecator = 1;
    }
    if (opCode== 2) {
        this.MsgType = "WRQ";
        indecator = 1;
    }
    if (opCode == 3) {
        this.MsgType = "DATA";
        indecator = -1;
    }
    if (opCode == 4) {
        this.MsgType = "ACK";
        indecator = -1;
    }
    if (opCode== 5) {
        this.MsgType = "ERROR";
        indecator = 1;
    }
    if (opCode == 6) {
        this.MsgType = "DIRQ";
        indecator = 0;
    }
    if (opCode == 7) {
        this.MsgType = "LOGRQ";
        indecator = 1;
    }
    if (opCode == 8) {
        this.MsgType = "DELRQ";
        indecator = 1;
    }
    if (opCode == 9) {
        this.MsgType = "BCAST";
        indecator = 1;
    }
    if (opCode == 10) {
        this.MsgType = "DISC";
        indecator = 0;
    }

    return  this.MsgType;
}
@Override
public byte[] decodeNextByte(byte nextByte) {
    // TODO: implement this

    //for the first byte which is part if the opcode
    if (len == 0) {
        pushByte(nextByte); //len++
        return null;
    }

    // for the second byte which defines the opcode
    if (len == 1) {
        pushByte(nextByte);
        byte[] toDefine = {bytes[0], bytes[1]};
        defineMsg(toDefine);
        if (indecator == 0) { //for DISC & DIRQ actions
            return popBytes();
        } else return null;
    }

    // for bytes after opcode
    else {

        //ack needs to be sent in 4 bytes
        if (MsgType == "ACK") {
            //adding byte number 3
            if (len == 2) {
                pushByte(nextByte);
                return null;
            }
            //else adding byte number 4
            else {
                pushByte(nextByte);
                return popBytes();
            }
        }
        //for actions that ends with zero, flagged with indicator 1
        if (indecator == 1) {
            if (nextByte != 0) {
                pushByte(nextByte);
            } else {
                pushByte(nextByte);
                return popBytes();
            }
        }

        //for action data
        if (MsgType == "DATA") {
            //first 5 bytes are adding regularly
            if (len < 5) {
                pushByte(nextByte);
                return null;
            }
            // adding byte number 6, defines the packet size yet to come
            if (len == 5) {
                byte[] bytesSize = {bytes[2], bytes[3]};
                packetSize = ByteBuffer.wrap(bytesSize).getShort();
                pushByte(nextByte);
                //if there will not be extra byte for packet to come
                if (packetSize == 0) {
                    return popBytes();
                } else return null;

                //next bytes are part of the packet
            } else {
                if (packetSize != 0) {
                    pushByte(nextByte);
                    packetSize--;
                }
                //when all packet sent, return the object.
                if (packetSize == 0) {
                    return popBytes();
                }
                return null;
            }
        }
        return null;
    }
}


private void pushByte(byte nextByte) {
    if (len >= bytes.length) {
        bytes = Arrays.copyOf(bytes, len * 2);
    }

    bytes[len++] = nextByte;
}

private byte[] popBytes() {
    byte[] msg = Arrays.copyOfRange(bytes, 0, len);
    len = 0;
    return msg;
}


@Override
public byte[] encode(byte[] message) {
    //TODO: implement this
    return message;
}
}