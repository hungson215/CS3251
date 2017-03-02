import java.net.SocketException;

public class RELDAT_Client {
    public static void main(String[] args) throws SocketException{
        RELDAT_Socket s;
        if(args.length == 0) {
            System.out.println("Usage: reldat_client [hostIP]:[Port] [window]");
        } else{
            String[] tokens = args[0].split(":");
            int wndwn = Integer.parseInt(args[1]);
            s = new RELDAT_Socket(2000);
            s.setRecvWndwn(wndwn);
        }
    }
}
