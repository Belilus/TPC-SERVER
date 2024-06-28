package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    TftpServerUsers loggedUserList;
    Queue<byte[]> responseToUserQueue;
    Queue<byte[]> incomingDataQueue;
    String userName;
    String pathToDir;
    String fileNameInProcess;
    boolean needToBcast;
    int connectionId;
    Connections<byte[]> connections;
    byte[] response;
    boolean shouldTerminate;
    public TftpProtocol(TftpServerUsers users){
        this.loggedUserList = users;
    }

    @Override 
    public void start(int connectionId, Connections<byte[]> connections) {
        this.shouldTerminate = false;
        this.needToBcast = false;
        this.connectionId = connectionId;
        this.connections = connections;
        this.pathToDir = "Files";        
    }

    @Override
    public void process(byte[] message) {
        // TODO implement this
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    private String extractStringFromMsg(byte[] message){
        String result = new String(message, 2, message.length - 3, StandardCharsets.UTF_8);
        return result;
    }
    private byte[] extractBytesFromMsg(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }
    private void generateGeneralAck() {
        OpcodeOperations op = new OpcodeOperations(Opcode.ACK);
        response = op.getGeneralAck();
    }
    
    //LOGRQ
    private void userLogin(byte[] message){
        userName = extractStringFromMsg(message);
        if(loggedUserList.isUserLoggedIn(userName)){
            generateError(7, "User already logged in");
        }
        else{
            loggedUserList.logInUser(userName, connectionId);
            generateGeneralAck();
        }
    }
    
    //DISC
    private void disconnect(){
        generateGeneralAck();
        if(response != null) connections.send(connectionId, encode(response));
        response = null;
        loggedUserList.logOutUser(connectionId);
        connections.disconnect(connectionId);
        shouldTerminate = true;        
    }
    //CONNECTED DISC adds zero byte at end of msg
    public byte[] encode(byte[] message) {
        if ((message != null) && (hasToAddZeroByte(message))){
            byte[] zero = {(byte) 0};
            byte[] modified = new byte[message.length + zero.length];
            System.arraycopy(message, 0, modified, 0, message.length);
            System.arraycopy(zero, 0, modified, message.length, zero.length);
            message = modified;
        }
        return message;
    }
    //CONNECTED DISC checks if needs to end with 0
    private boolean hasToAddZeroByte(byte[] message) {
        OpcodeOperations opcodeOperations = extractOpFromMessage(message);
        return opcodeOperations.shouldAddZero();
    }
    //CONNECTED DISC
    private OpcodeOperations extractOpFromMessage(byte[] message) {
        return new OpcodeOperations(message[1]);
    }
    
    //DELRQ
    private void fileToDelete(byte[] message){
        String fileToDelete = extractStringFromMsg(message);
        if(!fileWithThisNameExist(fileToDelete))
            generateError(1, "File not found");
        else{
            File file = getTheFile(fileToDelete);
            if(file.delete()){
                needToBcast = true;
                fileNameInProcess = fileToDelete;
                generateGeneralAck();
            }
            else
                generateError(2, "Access violation – File cannot be written, read or deleted.");
        }
    }

    //CONNECTED DELRQ
    private boolean fileWithThisNameExist(String fileName) {
        File file = getTheFile(fileName);
        return file.exists();
    }

    //CONNECTED DELRQ : creates or gets file from directory
    private File getTheFile(String fileName) {
        return new File(pathToDir + File.separator + fileName);
        
    }


    //ERROR
    private void generateError(int errorCode, String errorMsg) {
        OpcodeOperations opcodeOperations = new OpcodeOperations(Opcode.ERROR);
        byte[] errorPrefix = opcodeOperations.getInResponseFormat((byte) errorCode);
        byte[] errorMessage = extractBytesFromMsg(errorMsg);
        response = new byte[errorMessage.length + errorPrefix.length];
        System.arraycopy(errorPrefix, 0, response, 0, errorPrefix.length);
        System.arraycopy(errorMessage, 0, response, errorPrefix.length, errorMessage.length);
    }
    //ERROR
    private void processError(byte[] message) {
        System.out.println(extractStringFromMsg(message)); //For human use
    }

    //ACK
    private void processAck(byte[] message) {
        if (ackForPacket(message)){
            if (ackPacketSuccesses(message)){
                responseToUserQueue.remove(); //Packet was sent and received
                response = responseToUserQueue.peek();
            } else {
                throw new RuntimeException("The ACK packet that was received does not match the last packet that was send, received ACK for the packet number " + extractDataPacketNumber(message));
            }
        }
    }
    //CONNECTED ACK checks if packet num = block num
    private boolean ackPacketSuccesses(byte[] message) {
        return extractAckPacketNumber(message) == extractDataPacketNumber(responseToUserQueue.peek());
    }
    //CONNECTED ACK returns if it is a non 0 ACK
    private boolean ackForPacket(byte[] message) {
        return extractAckPacketNumber(message) != 0;
    }
    //CONNECTED ACK returns ack block num 
    private int extractAckPacketNumber(byte[] message) {
        return ((message[2] & 0xFF) << 8) | (message[3] & 0xFF);
    }
    //CONNECTED ACK returns data block num 
    private int extractDataPacketNumber(byte[] dataPacket) {
        return ((dataPacket[4] & 0xFF) << 8) | (dataPacket[5] & 0xFF);
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    } 

        

    
}
