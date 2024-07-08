package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessagingProtocol;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TftpProtocol {
    private OpcodeOperations opsFromServer;
    private OpcodeOperations opsToServer;

    private String fileNameForRRQ;
    private String fileNameForWRQ;
    private byte[] toFile;

    private String forDIRQ = "";
    private volatile boolean loggedIn = false;
    private volatile boolean connected = true;

    private Queue<byte[]> dataToSendWRQ = new ConcurrentLinkedQueue<>();

    //KEYBOARD THREAD
    public synchronized String process(String msg) {
        String command = msg.substring(0, msg.indexOf(' ') > -1 ? msg.indexOf(' ') : msg.length());
        if (compareCommand(command) != null) {
            opsToServer = new OpcodeOperations(command);
            if (opsToServer.opcode.equals(Opcode.DISC) && !loggedIn) {
                connected = false;
                return null;
            }
            if (opsToServer.opcode.equals(Opcode.RRQ)) {
                toFile  = new byte[]{};
                fileNameForRRQ = msg.substring(4);
                String currentDirectory = System.getProperty("user.dir");
                File file = new File(currentDirectory, fileNameForRRQ);
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (opsToServer.opcode.equals(Opcode.WRQ)) {
                fileNameForWRQ = msg.substring(4);
            }
        }
        return msg;
    }

    //LISTENER THREAD
    public synchronized byte[] process(byte[] msg) {
        opsFromServer = new OpcodeOperations(msg[1]);
        int blockNum;
        switch (opsFromServer.opcode) {
            case ACK:
                blockNum = ((msg[2] & 0xFF) << 8) | (msg[3] & 0xFF);
                System.out.println("ACK " + blockNum);
                if (blockNum == 0 && opsToServer.opcode.equals(Opcode.DISC)){
                    loggedIn = false;
                    connected = false;
                }
                if (blockNum == 0 && opsToServer.opcode.equals(Opcode.LOGRQ)) 
                    loggedIn = true;
                return new byte[]{msg[2], msg[3]};
            case BCAST:
                String fileName = new String(Arrays.copyOfRange(msg, 3, msg.length), StandardCharsets.UTF_8);
                if (msg[2] == 0) 
                    System.out.println("BCAST Deleted " + fileName);
                else 
                    System.out.println("BCAST Added " + fileName);
                return null;
            case ERROR:
                int errCode = ((msg[2] & 0xFF) << 8) | (msg[3] & 0xFF);
                String errMsg = new String(Arrays.copyOfRange(msg, 4, msg.length), StandardCharsets.UTF_8);
                System.out.println("ERROR " + errCode + " " + errMsg);
                resetInputs();
                return null;
            case DATA:
                int packetSize = ((msg[2] & 0xFF) << 8) | (msg[3] & 0xFF);
                blockNum = ((msg[4] & 0xFF) << 8) | (msg[5] & 0xFF);
                if (opsToServer.opcode == Opcode.RRQ) {
                    mergeToFile(msg, packetSize);
                    if (packetSize < 512) {
                        try {
                            FileOutputStream outputStream = new FileOutputStream(fileNameForRRQ);
                            outputStream.write(toFile);
                            outputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        System.out.println("RRQ " + fileNameForRRQ + " complete");
                    }
                    return generateACK(blockNum);
                }
                else if (opsToServer.opcode == Opcode.DIRQ) {
                    mergeForDIRQ(msg);
                    if (packetSize < 512) {
                        String[] toPrint = forDIRQ.split("\u0000");
                        forDIRQ = "";
                        for (String s : toPrint) {
                            System.out.println(s);
                        }
                    }
                    return generateACK(blockNum);
                }
        }
        return null;
    }
    
    public byte[] processWRQ(int packetNumACK) {
        if (dataToSendWRQ.isEmpty() && opsFromServer.opcode.equals(Opcode.ACK) && opsToServer.opcode.equals(Opcode.WRQ)) {
            File file = new File(fileNameForWRQ);
            byte[] data;
            try {
                FileInputStream fis = new FileInputStream(file);
                data = new byte[(int) file.length()];
                fis.read(data);
                createDataPackets(data);
                fis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        byte[] packet = dataToSendWRQ.peek();
        if (packet != null) {
            if (packetNumACK == 0 | packetNumACK == (((packet[4] & 0xFF) << 8) | (packet[5] & 0xFF))) {
                dataToSendWRQ.remove();
                packet = dataToSendWRQ.peek();
                if (packet == null) System.out.println("WRQ " + fileNameForWRQ + " complete");
            }
            else{
                System.out.println("ACK = " + packetNumACK +", packet = " + (((packet[4] & 0xFF) << 8) | (packet[5] & 0xFF)));
                throw new RuntimeException("ACK received doesn't match packet sent");
            }
        }
        return packet;

    }

    private void createDataPackets(byte[] data) {
        int numberOfPackets;
        numberOfPackets = (data.length / 512) + 1;
        dataToSendWRQ.add(generateDataPrefix(0, 0));
        for (int i = 1; i <= numberOfPackets; i++) {
            int sizeOfData = Math.min(512, data.length - ((i - 1) * 512));
            byte[] dataPacket = new byte[6 + sizeOfData];
            byte[] dataPrefix = generateDataPrefix(sizeOfData, i);
            System.arraycopy(dataPrefix, 0, dataPacket, 0, dataPrefix.length);
            System.arraycopy(data, (i - 1) * 512, dataPacket, 6, sizeOfData);
            dataToSendWRQ.add(dataPacket);
        }
    }

    private byte[] generateDataPrefix(int sizeOfData, int packetNum) {
        byte[] prefix = new byte[6];
        OpcodeOperations operations = new OpcodeOperations("DATA");
        System.arraycopy(operations.getInResponseFormat(), 0, prefix, 0, operations.getInResponseFormat().length);
        System.arraycopy(convertIntToByte(sizeOfData), 0, prefix, 2, 2);
        System.arraycopy(convertIntToByte(packetNum), 0, prefix, 4, 2);
        return prefix;

    }

    private byte[] convertIntToByte(int number) {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) ((number >> 8) & 0xFF);
        bytes[1] = (byte) (number & 0xFF);
        return bytes;
    }


    //Check if the command is in the list
    public String compareCommand(String command) {
        String[] commands = {"LOGRQ", "DELRQ", "RRQ", "WRQ", "DIRQ", "DISC"};
        for (String com : commands) if (com.equals(command)) return com;
        return null;
    }

    private void resetInputs() {
        opsFromServer = new OpcodeOperations(Opcode.UNDEFINED);
        opsToServer = new OpcodeOperations(Opcode.UNDEFINED);
        fileNameForRRQ = "";
        fileNameForWRQ = "";
        toFile = new byte[]{};
        forDIRQ = "";
        dataToSendWRQ = new ConcurrentLinkedQueue<>();
    }
    
    private void mergeToFile(byte[] msg, int packetSize) {
        byte[] merged = new byte[toFile.length + packetSize];
        System.arraycopy(toFile, 0, merged, 0, toFile.length);
        System.arraycopy(msg, 6, merged, toFile.length, packetSize);
        toFile = merged;
    }

    private void mergeForDIRQ(byte[] msg) {
        forDIRQ = forDIRQ + new String(Arrays.copyOfRange(msg, 6, msg.length), StandardCharsets.UTF_8);
    }

    private byte[] generateACK(int blockNum) {
        byte[] ack = new byte[4];
        ack[0] = 0;
        ack[1] = 4;
        ack[2] = (byte) ((blockNum >> 8) & 0xFF);
        ack[3] = (byte) (blockNum & 0xFF);
        return ack;
    }

    //@Override
    public boolean shouldTerminate() {
        return !(connected);
    }
}
