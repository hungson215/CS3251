import java.io.*;
import java.net.Inet4Address;
import java.net.SocketException;

public class RELDAT_Server {
    public static void main(String[] args) throws NumberFormatException, IOException,ClassNotFoundException{
        if(args.length != 2){
            System.out.println("Usage: reldat_server [port] [recvWindow]");
            System.exit(0);
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
            try {
                RELDAT_Socket s = new RELDAT_Socket(port, 1);
                s.setRecvWndwn(recvWndwn);


                int bytecount;
                while (true) {
                    System.out.println("Server started: " + Inet4Address.getLocalHost() + ":" + s.getPort());
                    System.out.println("Waiting for connection request");
                    s.accept();
                    System.out.println("The connection is established!");
                    while (true) {
                        try {
                            String res = s.receive();
                            FileInputStream recievedText = new FileInputStream(res);
                            String sending;
                            BufferedReader readFile = new BufferedReader(new FileReader(res));
                            String updated = "";
                            while ((sending = readFile.readLine()) != null) {
                                updated += sending.toUpperCase() + "\n";
                            }
                            recievedText.close();
                            FileOutputStream sendFile = new FileOutputStream(res);
                            sendFile.write(updated.getBytes());
                            sendFile.close();
                            s.send(res);

                        } catch (SocketException e) {
                            System.out.println("Client disconnected");
                            break;
                        } catch (IOException e) {
                            System.out.println("File not given");
                            break;
                        }
                    }
                }
            } catch (SocketException e ) {
                System.out.println("Could not create socket. exiting the program.");
            }
        }
    }
}
