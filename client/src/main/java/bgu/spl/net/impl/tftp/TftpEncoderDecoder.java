package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private final int size = 1 << 9;
    private byte[] bytes = new byte[size]; //start with 512 allegedly
    private OpcodeOperations opcodeOp;
    private int len = 0;
    private int packetSize;


    public byte[] decodeNextByte(byte nextByte) {
        if (len <= 4) {
            if (len == 1) 
                opcodeOp = new OpcodeOperations(nextByte);
            else {
                if (len == 4 && opcodeOp.opcode == Opcode.DATA){
                    packetSize = ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
                }
                else if(len == 3){
                    if (opcodeOp.opcode == Opcode.ACK) {
                        pushByte(nextByte);
                        return popBytes();
                    }
                }
            }
        } 
        else {
            if (opcodeOp.shouldWaitForZeroByte()) {
                if (nextByte == 0) 
                    return popBytes();
            } 
            else if (opcodeOp.opcode == Opcode.DATA && len == packetSize+5) {
                pushByte(nextByte);
                return popBytes();
            }
        }
        pushByte(nextByte);
        return null;
    }
    
    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }
        bytes[len++] = nextByte;
    }

    private byte[] popBytes() {
        byte[] result = Arrays.copyOfRange(bytes,0,len);
        len = 0;
        bytes = new byte[size];
        return result;
    }

    public byte[] encode(String op, String name) {
        OpcodeOperations opc = new OpcodeOperations(op);
        byte[] temp = opc.getInResponseFormat();
        if (name != null) {
            byte[] encoded;
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            encoded = new byte[2 + nameBytes.length];
            encoded[0] = temp[0];
            encoded[1] = temp[1];
            System.arraycopy(nameBytes, 0, encoded, 2, nameBytes.length);
            return encode(encoded);
        } else {
            return encode(temp);
        }
    }

    @Override
    public byte[] encode(byte[] msg) {
         if (Byte.toUnsignedInt(msg[1]) == 6 || Byte.toUnsignedInt(msg[1]) == 10 || Byte.toUnsignedInt(msg[1]) == 0 ) 
            return msg; // if it's ACK or DIRQ or UNDEFINED - send as is
        byte[] encodedMessage = new byte[msg.length + 1];
        System.arraycopy(msg, 0, encodedMessage, 0, msg.length);
        encodedMessage[msg.length] = 0;
        return encodedMessage;
    }
}