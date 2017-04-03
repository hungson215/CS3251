import java.io.File;
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
                String[] commandsInputs = inputs.split(" ");
                if (commandsInputs[0].equals("transform")) {
                    File textInput = new File(commandsInputs[1]);
                    if (commandsInputs[1].endsWith(".txt") && textInput.exists()) {
                        try {
                            s.send(commandsInputs[1]);
                            String received = s.receive();
                            if (received.contains("_received_received")) {
                                File textFile = new File(received.replaceFirst("_received", ""));
                                textFile.delete();
                                File textFile2 = new File(received);
                                textFile2.renameTo(textFile);
                            }
                        } catch (SocketException e) {
                            System.out.println("Server did not respond existing out");
                            break;
                        }
                    } else {
                        System.out.println("File does not exist");
                    }
                } else if(commandsInputs[0].equals("disconnect")) {
                    s.disconnect();
                    break;
                } else {
                    System.out.println("Wrong inputs the commands are transform <filename.txt> and disconnect.");
                }
            }
        }
    }
}
