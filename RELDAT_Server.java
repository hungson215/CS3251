import java.io.IOException;
import java.net.Inet4Address;

public class RELDAT_Server {
    public static void main(String[] args) throws NumberFormatException, IOException,ClassNotFoundException{
        RELDAT_Socket s;
        if(args.length == 0){
            System.out.println("Usage: reldat_server [port] [recvWindow]");
            return;
        } else {
            int port = Integer.parseInt(args[0]);
            int recvWndwn = Integer.parseInt(args[1]);
            s = new RELDAT_Socket(port,1);
            s.setRecvWndwn(recvWndwn);
        }
        System.out.println("Server started: " + Inet4Address.getLocalHost() + ":" + s.getPort());
        while(true) {
            s.accept();
            System.out.println("Connection established");
            s.receive();
            System.out.println("Transfer completed. Waiting for more data");
        }
    }
}
