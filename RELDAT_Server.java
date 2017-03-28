import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.SocketException;

public class RELDAT_Server {
    public static void main(String[] args) throws NumberFormatException, IOException,ClassNotFoundException{
        RELDAT_Socket s;
        if(args.length != 2){
            System.out.println("Usage: reldat_server [port] [recvWindow]");
            return;
        } else {
            int port = Integer.parseInt(args[0]);
            if (port <= 1024 && (port >= 65535)) {
                System.out.println("Port number must be between 1024 and 65535");
                System.exit(0);
            }
            int recvWndwn = Integer.parseInt(args[1]);
            if (recvWndwn <= 0) {
                System.out.println("window can't be lower than 1");
                System.exit(0);
            }
            s = new RELDAT_Socket(port,1);
            s.setRecvWndwn(recvWndwn);
        }
        int bytecount;
        while(true) {
            System.out.println("Server started: " + Inet4Address.getLocalHost() + ":" + s.getPort());
            System.out.println("Waiting for connection request");
            s.accept();
            System.out.println("Connection established!");
            while(true) {
                try {
                    String res = s.receive();
                    FileInputStream recievedText = new FileInputStream(res);
                    byte[] byteText = new byte[1000];
                    String sending = "";
                    while ((bytecount = recievedText.read(byteText, 0, 1000)) != -1) {
                        sending += new String(byteText).toUpperCase();
                    }
                    recievedText.close();
                    FileOutputStream sendFile = new FileOutputStream(res);
                    sendFile.write(sending.getBytes());
                    sendFile.close();
                    s.send(res);
                    /*
                    if (res != null) {
                        System.out.println("Client's message: " + res);
                        s.send(res.toUpperCase());
                    } */
                }catch (SocketException e) {
                    break;
                }
            }
        }
    }
}
