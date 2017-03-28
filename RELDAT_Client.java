import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Scanner;

public class RELDAT_Client {
    public static void main(String[] args) throws ClassNotFoundException,IOException{
        RELDAT_Socket s;
        if(args.length != 3) {
            System.out.println("Usage: reldat_client [hostIP] [Port] [window]");
        } else{
            String Ipaddress = args[0];
            int wndwn = Integer.parseInt(args[2]);
            if (wndwn <= 0) {
                System.out.println("window can't be lower than 1");
                System.exit(0);
            }
            s = new RELDAT_Socket(2000,1);
            s.setRecvWndwn(wndwn);
            int port = Integer.parseInt(args[1]);
            if (port <= 1024 && (port >= 65535)) {
                System.out.println("Port number must be between 1024 and 65535");
                System.exit(0);
            }
            s.connect(Ipaddress,port);
            if (s.getState() == RELDAT_Socket.CONNECTION_STATE.CLOSED) {
                System.out.println("Connection could not be established closing the client");
                System.exit(0);
            }
            System.out.println("Connection established!");

            Scanner scan = new Scanner(System.in);
            while(true) {
                System.out.print("Please enter your command:");
                String inputs = scan.nextLine();
                if (inputs.toLowerCase().startsWith("transform")) {
                    String[] filename = inputs.split(" ");
                    s.send(filename[1]);
                    s.receive();
                } else if(inputs.toLowerCase().startsWith("disconnect")) {
                    s.disconnect();
                    break;
                } else {
                    System.out.println("Wrong inputs the commands are transform and disconnect.");
                }
            }
        }
    }
}
