import java.io.*;
import java.net.*;
import java.util.ArrayList;


public class RELDAT_Socket {
    private static int MSS = 1000; //Maximum segment size
    private int seq;    //Sequence number
    private int ack;    //Acknowledge number = sender seq + received packet's length
    private int recvWndwn;  // Receiver's window size
    private int senderWndwn; // Sender's window size
    private DatagramSocket s;
    private InetAddress remoteAddr;
    private int remotePort;
    private CONNECTION_STATE state;
    public enum CONNECTION_STATE {LISTEN, ESTABLISHED, CLOSED}

    public InetAddress getLocalAddress() {
        return s.getLocalAddress();
    }
    public int getPort() {
        return s.getLocalPort();
    }
    public int getRecvWndwn() {
        return recvWndwn;
    }

    public void setRecvWndwn(int recvWndwn) {
        this.recvWndwn = recvWndwn;
    }

    /**
     * Wrap RELDAT_packet inside a DatagramPacket
     */
    private DatagramPacket Pack(RELDAT_Packet data) throws IOException{
        //Convert an object to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(data);
        oos.flush();
        byte[] output = baos.toByteArray();

        return new DatagramPacket(output, output.length, remoteAddr, remotePort);
    }

    /**
     * Remove DatagramPacket header
     */
    private RELDAT_Packet Unpack(DatagramPacket p) throws IOException, ClassNotFoundException{
        byte[] data = p.getData();
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bais);
        return (RELDAT_Packet)ois.readObject();
    }

    /**
     * Split byte array data into packets for transferring over UDP
     * @param data
     * @return
     */
    private ArrayList<DatagramPacket> Packetize(byte[] data) throws IOException{
        ArrayList<DatagramPacket> packets = new ArrayList<>();
        //If data size less than MSS then put everything into 1 packet
        if(data.length  < MSS) {
            RELDAT_Packet reldat_packet = new RELDAT_Packet(data,seq,ack, RELDAT_Packet.TYPE.DATA,recvWndwn);
            DatagramPacket p = Pack(reldat_packet);
            packets.add(p);
            //Else, split data into multiple packets
        } else {
            byte[] buffer;
            int i = 0;
            while(i < data.length) {
                RELDAT_Packet reldat_packet;
                if(i + MSS < data.length) {
                    buffer = new byte[MSS];
                    System.arraycopy(data, i, buffer, 0, MSS);
                    reldat_packet = new RELDAT_Packet(buffer, seq, ack, RELDAT_Packet.TYPE.DATA, recvWndwn);
                    seq += MSS;
                } else{
                    buffer = new byte[data.length - i];
                    System.arraycopy(data, i, buffer, 0, data.length - i);
                    reldat_packet = new RELDAT_Packet(buffer,seq, ack, RELDAT_Packet.TYPE.DATA,recvWndwn);
                    seq = data.length;
                }
                DatagramPacket p = Pack(reldat_packet);
                packets.add(p);
                i+= MSS;
            }
        }
        return packets;
    }

    /**
     * Establish a reliable connection using 3-way handshake
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void accept() throws IOException, ClassNotFoundException {
        state = CONNECTION_STATE.LISTEN;
        byte[] buffer = new byte[MSS];
        DatagramPacket p = new DatagramPacket(buffer,buffer.length);
        s.receive(p); // Wait for connection request
        RELDAT_Packet reldat_packet = Unpack(p);
        //If SYN request received
        if (reldat_packet.getType() == RELDAT_Packet.TYPE.SYN) {
            ack = reldat_packet.getSeq() + reldat_packet.getLength();
            senderWndwn = reldat_packet.getWndwn();
            remoteAddr = p.getAddress();
            remotePort = p.getPort();

            reldat_packet = new RELDAT_Packet(new byte[1], seq, ack, RELDAT_Packet.TYPE.SYNACK, recvWndwn);
            seq += 1;
            p = Pack(reldat_packet);
            s.setSoTimeout(5000); // Set timout for 5s
            int retry = 0;
            //Wait and resend SYNACK when needed
            while(true) {
                try {
                    s.send(p);
                    buffer = new byte[MSS];
                    p = new DatagramPacket(buffer, buffer.length);
                    while(true) {
                        s.receive(p);
                        reldat_packet = Unpack(p);
                        //If ACK of SYNACK received
                        if ((reldat_packet.getType() == RELDAT_Packet.TYPE.ACK) && (seq == reldat_packet.getAck())) {
                            state = CONNECTION_STATE.ESTABLISHED;
                            break;
                            //If the ACK of SYNACK is lost
                        } else if(reldat_packet.getType() == RELDAT_Packet.TYPE.DATA){
                            state = CONNECTION_STATE.ESTABLISHED;

                        }
                    }
                    break;
                }catch (SocketTimeoutException e){
                    System.out.println("Timeout. Try to resend SYNACK...");
                    if(retry > 3) {
                        throw new IOException("Client is unreachable");
                    }
                    retry++;
                }
            }
        }
    }

    public void connect(String serverAddr, int serverPort) throws IOException,ClassNotFoundException {
        byte[] buffer = new byte[1];
        this.seq = 0;
        this.remoteAddr = InetAddress.getByName(serverAddr);
        this.remotePort = serverPort;
        RELDAT_Packet reldat_packet = new RELDAT_Packet(buffer,seq,ack, RELDAT_Packet.TYPE.SYN,recvWndwn);
        DatagramPacket p = Pack(reldat_packet);
        s.setSoTimeout(3000);
        int retry = 0;
        while (true) {
            try {
                s.send(p);
                buffer = new byte[MSS];
                p = new DatagramPacket(buffer,buffer.length);
                s.receive(p);
                reldat_packet = Unpack(p);
                if(reldat_packet.getType() == RELDAT_Packet.TYPE.SYNACK) {
                    ack = reldat_packet.getSeq() + reldat_packet.getLength();
                    senderWndwn = reldat_packet.getWndwn();
                    remoteAddr = p.getAddress();
                    remotePort = p.getPort();
                }
                seq += 1;
                reldat_packet = new RELDAT_Packet(new byte[1], seq, ack, RELDAT_Packet.TYPE.ACK, recvWndwn);
                p = Pack(reldat_packet);
                s.send(p);
                state = CONNECTION_STATE.ESTABLISHED;
                break;
            }catch (SocketTimeoutException e){
                if(retry < 3) {
                    System.out.println("Timeout! Try to resend SYN...");
                } else {
                    System.out.println("Closing the connection");
                    state = CONNECTION_STATE.CLOSED;
                    break;
                }
            }
        }
    }

    /**
     * Constructor
     * @param localPort
     * @throws SocketException
     */
    public RELDAT_Socket(int localPort)throws SocketException{
        s = new DatagramSocket(localPort);
        state = CONNECTION_STATE.CLOSED;
    }


}