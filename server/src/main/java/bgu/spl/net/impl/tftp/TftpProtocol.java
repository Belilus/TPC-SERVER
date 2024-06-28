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
    TftpServerUsers userList = new TftpServerUsers();
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
        this.userList = users;
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
        String result = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);
        return result;
    }
    private byte[] extractBytesFromMsg(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }
    private void generateGeneralAck() {
        OpcodeOperations op = new OpcodeOperations(Opcode.ACK);
        response = op.getGeneralAck();
    }

    private void userLogin(byte[] message){
        userName = extractStringFromMsg(message);
        if(userList.isUserLoggedIn(userName)){
            generateError(7, "User already logged in");
        }
        else{
            userList.logInUser(userName, connectionId);
            generateGeneralAck();
        }
    }
    private 

    private void generateError(int errorCode, String errorMsg) {
        OpcodeOperations opcodeOperations = new OpcodeOperations(Opcode.ERROR);
        byte[] errorPrefix = opcodeOperations.getInResponseFormat((byte) errorCode);
        byte[] errorMessage = extractBytesFromMsg(errorMsg);
        response = new byte[errorMessage.length + errorPrefix.length];
        System.arraycopy(errorPrefix, 0, response, 0, errorPrefix.length);
        System.arraycopy(errorMessage, 0, response, errorPrefix.length, errorMessage.length);
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    } 


    
}
