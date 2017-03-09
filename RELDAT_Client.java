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
            System.out.println("Connection established");

            Scanner scan = new Scanner(System.in);
            System.out.print("Please enter your command:");
            String[] inputs = scan.nextLine().split(" ");
            if(inputs[0].compareTo("transform")==0) {
                s.send(inputs[1]);
            }
        }
    }
}
