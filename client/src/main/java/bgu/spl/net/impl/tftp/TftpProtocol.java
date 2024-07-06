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

    //process for the keyboardThread
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

    //Check if the command is in the list
    public String compareCommand(String command) {
        String[] commands = {"LOGRQ", "DELRQ", "RRQ", "WRQ", "DIRQ", "DISC"};
        for (String com : commands) if (com.equals(command)) return com;
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
}
