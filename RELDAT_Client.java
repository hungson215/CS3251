import java.io.IOException;
import java.net.SocketException;
import java.util.Scanner;

public class RELDAT_Client {
    public static void main(String[] args) throws SocketException,ClassNotFoundException,IOException{
        RELDAT_Socket s;
        if(args.length == 0) {
            System.out.println("Usage: reldat_client [hostIP]:[Port] [window]");
        } else{
            String[] tokens = args[0].split(":");
            int wndwn = Integer.parseInt(args[1]);
            s = new RELDAT_Socket(2000,1);
            s.setRecvWndwn(wndwn);
            s.connect("10.0.75.1",4000);
            System.out.println("Connection established!");

            Scanner scan = new Scanner(System.in);
            while(true) {
                System.out.print("Please enter your command:");
                String inputs = scan.nextLine();
                if (inputs.toLowerCase().startsWith("transform")) {
                    String[] filename = inputs.split(" ");
                    s.send(filename[1]);
                } else if(inputs.toLowerCase().startsWith("disconnect")) {
                    s.disconnect();
                    break;
                } else {
                    s.send(inputs);
                    String res = s.receive();
                    if (res != null) {
                        System.out.println("Server's response: " + res);
                    }
                }
            }
        }
    }
}
