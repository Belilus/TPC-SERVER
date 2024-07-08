package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.Server;

public class TftpServer {
    public static void main(String[] args) {
        TftpServerUsers userList = new TftpServerUsers();
        Server.threadPerClient(Integer.parseInt("7777"),()-> new TftpProtocol(userList), TftpEncoderDecoder::new).serve();
    }
}
