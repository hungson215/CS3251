import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;

/**
 * Created by David Nguyen on 3/2/2017.
 */
public class RELDAT_Socket {
    private static int MSS = 1000; //Maximum segment size
    private int seq;    //Sequence number
    private int ack;    //Acknowledge number
    private int wndwn;  // Window size
    private DatagramSocket s;
    private int local1Port;
    private InetAddress remoteAddr;
    private int remotePort;

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
     * Split byte array data into packets
     * @param data
     * @return
     */

    private ArrayList<DatagramPacket> Packetize(byte[] data) throws IOException{
        ArrayList<DatagramPacket> packets = new ArrayList<>();
        if(data.length  < MSS) {
            RELDAT_Packet reldat_packet = new RELDAT_Packet(data,seq,ack, RELDAT_Packet.TYPE.DATA,wndwn);
            DatagramPacket p = Pack(reldat_packet);
            packets.add(p);
        } else {
            byte[] buffer;
            int i = 0;
            while(i < data.length) {
                RELDAT_Packet reldat_packet;
                if(i + MSS < data.length) {
                    buffer = new byte[MSS];
                    System.arraycopy(data, i, buffer, 0, MSS);
                    reldat_packet = new RELDAT_Packet(buffer, seq, ack, RELDAT_Packet.TYPE.DATA, wndwn);
                    seq += MSS;
                } else{
                    buffer = new byte[data.length - i];
                    System.arraycopy(data, i, buffer, 0, data.length - i);
                    reldat_packet = new RELDAT_Packet(buffer,seq, ack, RELDAT_Packet.TYPE.DATA,wndwn);
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
     * Constructor
     * @param localPort
     * @throws SocketException
     */
    public RELDAT_Socket(int localPort)throws SocketException{
        s = new DatagramSocket(localPort);
    }


}