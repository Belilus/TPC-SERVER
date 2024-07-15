package bgu.spl.net.impl.tftp;

import java.io.*;
import java.net.Socket;

public class TftpClient {
    private Socket socket;
    private BufferedReader keyboardReader;
    private InputStream in;
    private OutputStream out;
    private Thread keyboardThread;
    private Thread listeningThread;

    private int numACK;
    private boolean doneWRQ = true;

    private final TftpProtocol protocol = new TftpProtocol();
    private final TftpEncoderDecoder encdec = new TftpEncoderDecoder();


    public TftpClient(String serverAddress, int port) {
        try {
            socket = new Socket(serverAddress, port);
            keyboardReader = new BufferedReader(new InputStreamReader(System.in));
            in = socket.getInputStream();
            out = socket.getOutputStream();

        } 
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void close() {
        try {
            socket.close();
        } 
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void start() {
        keyboardThread = new Thread(() -> {
            try {
                System.out.println("Keyboard thread started");
                String userInput;
                String userOp;
                while (!protocol.shouldTerminate()) {
                    String fileName = null; // Declare fileName here
                    // Remove the ready() check and readLine() directly
                    if ((userInput = keyboardReader.readLine()) != null) {
                        System.out.println("User input: " + userInput);
                        userOp = userInput.substring(0, userInput.indexOf(' ') > -1 ? userInput.indexOf(' ') : userInput.length());
                        if (!(userOp.equals("DIRQ") || userOp.equals("DISC"))) {
                            if ((userOp = protocol.compareCommand(userOp)) != null) {
                                fileName = userInput.substring(userOp.length() + 1);
                            }
                        }
                        if ((protocol.process(userInput)) != null) {
                            send(encdec.encode(userOp, fileName));
                            if (userOp != null && userOp.equals("WRQ")) {
                                doneWRQ = false;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        keyboardThread.start();
        
        listeningThread = new Thread(() -> {
            try {
                System.out.println("Listening thread started");
                int read;
                byte[] serverInput;
                while (!protocol.shouldTerminate()) {
                    if (in.available() > 0) {
                        //System.out.println("Input stream is available");
                        read = in.read();
                        if (read >= 0) {
                            serverInput = encdec.decodeNextByte((byte) read);
                            if (serverInput != null) {
                                byte[] response = protocol.process(serverInput);
                                if (response != null) {
                                    if (response.length > 2) 
                                        send(response);
                                    else if (response.length == 2) {
                                        numACK = ((response[0] & 0xFF) << 8) | (response[1] & 0xFF);
                                        if (!doneWRQ) 
                                            sendPackets();
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        listeningThread.start();
    }
    
   

    private void sendPackets() {
        byte[] packet;
        if ((packet = protocol.processWRQ(numACK)) != null) 
            send(packet);
        else 
            doneWRQ = true;
    }

    private synchronized void send(byte[] msg) {
        try {
            out.write(msg);
            out.flush();
        } 
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private void waitForThreads() {
        try {
            keyboardThread.join();
            listeningThread.join();
        } 
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    
    //TODO: implement the main logic of the client, when using a thread per client the main logic goes here
    public static void main(String[] args) {
        String serverAddress = "10.0.0.36";
        // String serverAddress = args[0];
        int port = 7777;
        // int port = Integer.parseInt(args[1]);
        TftpClient client = new TftpClient(serverAddress, port);
        client.start();
        client.waitForThreads();
        client.close();
    }
}

