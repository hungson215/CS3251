import javax.xml.crypto.Data;
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
    public enum CONNECTION_STATE {LISTEN, ESTABLISHED, CLOSED, FIN_WAIT,CLOSE_WAIT,LAST_ACK}
    private byte[] receivedBuffer; //Receiver's Buffer
    private int debug;

    public InetAddress getLocalAddress() {
        return s.getLocalAddress();
    }
    public int getPort() {
        return s.getLocalPort();
    }
    public int getRecvWndwn() {
        return recvWndwn;
    }

    public void setRecvWndwn(int recvWndwn)
    {
        this.recvWndwn = recvWndwn;
        receivedBuffer = new byte[recvWndwn];
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
        byte[] data = new byte[p.getLength()];
        System.arraycopy(p.getData(),0,data,0,p.getLength());
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bais);
        return (RELDAT_Packet)ois.readObject();
    }

    /**
     * Split byte array data into packets for transferring over UDP
     * @param data
     * @return
     */
    private ArrayList<DatagramPacket> Packetize(byte[] data, int length) throws IOException{
        ArrayList<DatagramPacket> packets = new ArrayList<>();
        //If data size less than MSS then put everything into 1 packet
        if(length  < MSS) {
            RELDAT_Packet reldat_packet = new RELDAT_Packet(data,length,seq,ack, RELDAT_Packet.TYPE.DATA,recvWndwn);
            DatagramPacket p = Pack(reldat_packet);
            packets.add(p);
            seq+= length;
            //Else, split data into multiple packets
        } else {
            byte[] buffer;
            int i = 0;
            while(i < length) {
                RELDAT_Packet reldat_packet;
                if(i + MSS <= length) {
                    buffer = new byte[MSS];
                    System.arraycopy(data, i, buffer, 0, MSS);
                    reldat_packet = new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.DATA, recvWndwn);
                    seq += MSS;
                } else{
                    buffer = new byte[length - i];
                    System.arraycopy(data, i, buffer, 0, length - i);
                    reldat_packet = new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.PUSH,recvWndwn);
                    seq += length - i;
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
            if(debug == 1) {
                System.out.println("SYN Request Received from " + p.getAddress().toString());
            }
            ack = reldat_packet.getSeq() + reldat_packet.getLength();
            senderWndwn = reldat_packet.getWndwn();
            remoteAddr = p.getAddress();
            remotePort = p.getPort();

            reldat_packet = new RELDAT_Packet(new byte[1],1, seq, ack, RELDAT_Packet.TYPE.SYNACK, recvWndwn);
            if(debug == 1) {
                System.out.println("Send SYNACK to " + remoteAddr.toString());
            }
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
                            if(debug ==1) {
                                System.out.println("ACK of SYNACK Received. Connection is established!");
                            }
                            state = CONNECTION_STATE.ESTABLISHED;
                            ack += reldat_packet.getLength();
                            break;
                            //If the ACK of SYNACK is lost.
                        } else if(reldat_packet.getType() == RELDAT_Packet.TYPE.DATA){
                            //Assume that the connection is established
                            if(debug == 1) {
                                System.out.println("Expect ACK of SYNACK but received DATA");
                            }
                            state = CONNECTION_STATE.ESTABLISHED;
                            ack = reldat_packet.getSeq() + reldat_packet.getLength();
                            //Add code to store data and send ACK
                            System.arraycopy(reldat_packet.getData(),0,receivedBuffer,0,reldat_packet.getLength());
                        }
                    }
                    break;
                }catch (SocketTimeoutException e){
                    System.out.println("Timeout. Try to resend SYNACK...");
                    if(retry > 3) {
                        throw new IOException("Client is unreachable.");
                    }
                    retry++;
                }
            }
        }
        System.out.println("---SERVER STATUS---");
        System.out.println("localPort:" + s.getLocalPort());
        System.out.println("localAddress" + Inet4Address.getLocalHost());
        System.out.println("Seq:" + seq);
        System.out.println("Ack:" + ack);
        System.out.println("RecvWindown:" + recvWndwn);
        System.out.println("SenderWindow:" + senderWndwn);
        System.out.println("remoteAddress:" + remoteAddr.toString());
        System.out.println("remotePort:" + remotePort);
    }

    public void connect(String serverAddr, int serverPort) throws IOException,ClassNotFoundException {
        byte[] buffer = new byte[1];
        this.remoteAddr = InetAddress.getByName(serverAddr);
        this.remotePort = serverPort;
        RELDAT_Packet reldat_packet = new RELDAT_Packet(buffer,1, seq,ack, RELDAT_Packet.TYPE.SYN,recvWndwn);
        seq+=1;
        DatagramPacket p = Pack(reldat_packet);
        s.setSoTimeout(3000);
        int retry = 0;
        while (true) {
            try {
                if(debug == 1) {
                    System.out.println("Send out SYN request to " + serverAddr.toString());
                }
                s.send(p);
                buffer = new byte[MSS];
                p = new DatagramPacket(buffer,buffer.length);
                s.receive(p);
                reldat_packet = Unpack(p);
                if(reldat_packet.getType() == RELDAT_Packet.TYPE.SYNACK) {
                    if(debug == 1) {
                        System.out.println("SYNACK received!");
                    }
                    ack = reldat_packet.getSeq() + reldat_packet.getLength();
                    senderWndwn = reldat_packet.getWndwn();
                    remoteAddr = p.getAddress();
                    remotePort = p.getPort();
                }
                reldat_packet = new RELDAT_Packet(new byte[1],1, seq, ack, RELDAT_Packet.TYPE.ACK, recvWndwn);
                seq+=1;
                p = Pack(reldat_packet);
                if(debug == 1) {
                    System.out.println("Send out ACK of SYNACK and establish the connection");
                }
                s.send(p);
                state = CONNECTION_STATE.ESTABLISHED;
                break;
            }catch (SocketTimeoutException e){
                if(retry < 3) {
                    System.out.println("Timeout! Try to resend SYN...");
                    retry++;
                } else {
                    System.out.println("Closing the connection");
                    state = CONNECTION_STATE.CLOSED;
                    break;
                }
            }
        }
        System.out.println("---CLIENT STATUS---");
        System.out.println("localPort:" + s.getLocalPort());
        System.out.println("localAddress" + Inet4Address.getLocalHost());
        System.out.println("Seq:" + seq);
        System.out.println("Ack:" + ack);
        System.out.println("RecvWindown:" + recvWndwn);
        System.out.println("SenderWindow:" + senderWndwn);
        System.out.println("remoteAddress:" + remoteAddr.toString());
        System.out.println("remotePort:" + remotePort);
    }
    public void send(String filename) throws IOException,ClassNotFoundException {
        FileInputStream fis = new FileInputStream(filename);
        byte[] sendData = new byte[recvWndwn];
        int byteCount;

        //Initiate file transfer
        String initStr = "INIT_FILE_TRANSFER:" + filename;
        byte[] initBStr = initStr.getBytes();
        RELDAT_Packet init = new RELDAT_Packet(initBStr, initBStr.length, seq, ack, RELDAT_Packet.TYPE.PUSH, senderWndwn);
        seq += initBStr.length;
        int retry = 0;
        while(true) {
            try{
                s.send(Pack(init));
                byte[] response = new byte[MSS];
                DatagramPacket p = new DatagramPacket(response,response.length);
                s.receive(p);
                RELDAT_Packet res = Unpack(p);
                if(res.getAck() == seq) {
                    ack+= res.getLength();
                    break;
                }
            } catch (SocketException e) {
                if(debug == 1) {
                    System.out.println("Time out try to resending");
                }
                retry++;
                if(retry > 3) {
                    if(debug == 1) {
                        System.out.println("Failed to initiate file transfer!");
                    }
                    return;
                }
            }
        }
        //Begin file transfer
        if(debug == 1) {
            System.out.println("Prepare data to transfer...");
        }
        //Prepare data to transfer
        ArrayList<DatagramPacket> packets = new ArrayList<>();
        while((byteCount = fis.read(sendData,0,recvWndwn))!= -1) {
            packets.addAll(Packetize(sendData,byteCount));
        }
        RELDAT_Packet push_packet = Unpack(packets.remove(packets.size() - 1));
        push_packet.setType(RELDAT_Packet.TYPE.PUSH);
        packets.add(Pack(push_packet));
        fis.close();
        if(debug == 1) {
            System.out.println("Total packets need to send: " + packets.size());
            System.out.println("Start sending data...");
        }
        //Start sending data
        while(!packets.isEmpty()) {
            byteCount = 0;
            int i = 0;
            //Sending first window
            while (byteCount < recvWndwn && i < packets.size()) {
                s.send(packets.get(i));
                byteCount += packets.get(i).getLength();
                i++;
            }
            int index = i - 1;
            //int count = 0;
            //int lossDetector = 0;

            //Start waiting for ACK and sending new packets
            while (!packets.isEmpty()) {
                byte[] buffer = new byte[MSS];
                DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                if (debug == 1) {
                    System.out.println("Waiting for ACK");
                }
                s.receive(p);
                //Packet received
                RELDAT_Packet reldat_recvpacket = Unpack(p);
                if(debug == 1) {
                    System.out.println("Received ACK for packet with seq# "+ reldat_recvpacket.getAck());
                }
                //Check if the received packet is ACK
                if(reldat_recvpacket.getType() == RELDAT_Packet.TYPE.ACK) {
                    //Check if the received packet is expected
                    if (ack == reldat_recvpacket.getSeq()) {
                        //Update ack number
                        ack += reldat_recvpacket.getLength();
                        //Remove the first packet as it is sent successfully
                        packets.remove(0);
                        //Send the new packet if there is any
                        if (index < packets.size()) {
                            if(debug == 1) {
                                System.out.println("Send the new packet with seq# " + Unpack(packets.get(index)).getSeq());
                            }
                            s.send(packets.get(index));
                        } else if (debug == 1){
                            System.out.println("No new packet to send");
                        }
                        //If some acks are lost, assume sent data are already received based on the last ack received
                    } else if (ack < reldat_recvpacket.getSeq()) {
                        //Update the ack number
                        ack = reldat_recvpacket.getSeq() + reldat_recvpacket.getLength();
                        //Need to send multiple packets
                        while (true) {
                            //Remove the first packet
                            packets.remove(0);
                            //Check if there is any packet left
                            if (index < packets.size()) {
                                RELDAT_Packet reldat_packet = Unpack(packets.get(1));
                                if (reldat_packet.getSeq() <= reldat_recvpacket.getAck()) {
                                    s.send(packets.get(index));
                                } else {
                                    break;
                                }
                            } else {
                                if(debug == 1) {
                                    System.out.println("There's no new packet to send");
                                }
                                break;
                            }
                        }
                    }
                }
/*
                if(packets.size() > 1) {

                    if (reldat_recvpacket.getAck() >= reldat_sendpacket.getSeq()) {
                        if (debug == 1) {
                            System.out.println("Packet " + count + "received successfully");
                            System.out.println("Sending new packet");
                        }


                        count++;
                        //After receiving 3 ACK with the same ack number, resending the whole window size
                    } else if (lossDetector > 3) {
                        if (debug == 1) {
                            System.out.println("Packet loss detected!");
                            System.out.println("Start resending");
                        }
                        break;
                    } else {
                        lossDetector++; //Increase the lossDetector to signal Fast Retransmit
                    }
                }*/
            }
            if(debug == 1) {
                System.out.println("Transfer completed!");
            }
        }
    }
    public void receive() throws IOException,ClassNotFoundException{
        s.setSoTimeout(0);
        String filename ="";
        FileOutputStream fos = null;
        if(debug == 1) {
            System.out.println("Start receiving data....");
        }
        while(true) {
            byte[] buffer = new byte[2*MSS];
            DatagramPacket p = new DatagramPacket(buffer, buffer.length);
            s.receive(p);
            RELDAT_Packet reldat_packet = Unpack(p);
            if(reldat_packet.getSeq() == ack) {
                if ((reldat_packet.getType() == RELDAT_Packet.TYPE.DATA) ||
                        (reldat_packet.getType() == RELDAT_Packet.TYPE.PUSH)) {
                    if (debug == 1) {
                        System.out.println("Correct packet received");
                    }
                    if (fos != null) {
                        if (debug == 1) {
                            System.out.println("Write data packet with seq#" + reldat_packet.getSeq() + " to the file");
                        }
                        fos.write(reldat_packet.getData(), 0, reldat_packet.getLength());
                    }
                    ack += reldat_packet.getLength();
                    buffer = new byte[1];
                    RELDAT_Packet reldat_ackpacket = new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.ACK, senderWndwn);
                    seq += buffer.length;
                    p = Pack(reldat_ackpacket);

                    if (debug == 1) {
                        System.out.println("Sending ACK and waiting for packet with seq# " + ack);
                    }
                    s.send(p);
                    if (reldat_packet.getType() == RELDAT_Packet.TYPE.PUSH) {
                        if (filename.isEmpty()) {
                            String init = new String(reldat_packet.getData());
                            if (init.startsWith("INIT_FILE_TRANSFER:")) {
                                int i = init.lastIndexOf(':');
                                int j = init.lastIndexOf('.');
                                filename = init.substring(i+1,j) + "_received" + init.substring(j);
                            } else {
                                filename = "noname.tmp";
                            }
                            fos = new FileOutputStream(filename);
                            continue;
                        }
                    }
                    if (debug == 1) {
                        System.out.println("Received All Data");
                    }
                    if (fos != null) {
                        if (debug == 1) {
                            System.out.println("Close the file");
                        }
                        fos.flush();
                        fos.close();
                    }
                    break;
                    //If the sender start Disconnecting
                } else if (reldat_packet.getType() == RELDAT_Packet.TYPE.FIN) {
                    if(state == CONNECTION_STATE.ESTABLISHED) {
                        state = CONNECTION_STATE.CLOSE_WAIT;
                        //Send ACK for FIN
                        ack += reldat_packet.getLength();
                        buffer = new byte[1];
                        RELDAT_Packet reldat_ackpacket = new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.ACK, senderWndwn);
                        seq += buffer.length;
                        buffer = new byte[1];
                        RELDAT_Packet reldat_finpacket = new RELDAT_Packet(buffer,buffer.length,seq,ack, RELDAT_Packet.TYPE.FIN,senderWndwn);
                        seq += buffer.length;
                        DatagramPacket ackpacket = Pack(reldat_ackpacket);
                        DatagramPacket finpacket = Pack(reldat_finpacket);
                        while (true) {
                            s.send(ackpacket);
                            s.send(finpacket);
                            buffer = new byte[MSS];
                            p = new DatagramPacket(buffer,buffer.length);
                            s.receive(p);
                            RELDAT_Packet res = Unpack(p);
                            if((res.getType() == RELDAT_Packet.TYPE.ACK) &&
                                    (res.getAck() == seq)){
                                state = CONNECTION_STATE.CLOSED;
                            }
                        }
                    }
                }
            } else {
                break;
            }
        }
    }
    public void disconnect() throws IOException, ClassNotFoundException{
        byte[] buffer = new byte[1];
        RELDAT_Packet reldat_packet = new RELDAT_Packet(buffer,buffer.length,seq,ack, RELDAT_Packet.TYPE.FIN,recvWndwn);
        seq+=buffer.length;
        DatagramPacket p = Pack(reldat_packet);
        s.setSoTimeout(2000);
        int retry = 0;
        while(true) {
            try {
                s.send(p);
                buffer = new byte[MSS];
                DatagramPacket res = new DatagramPacket(buffer,buffer.length);
                s.receive(res);
                RELDAT_Packet reldat_res = Unpack(res);
                if(reldat_res.getAck() == seq) {
                    if (reldat_res.getType() == RELDAT_Packet.TYPE.ACK) {

                    } else if (reldat_res.getType() == RELDAT_Packet.TYPE.FIN){

                    }
                }
            }catch(SocketTimeoutException e) {
                if(retry>3) {
                    break;
                }
                System.out.println("Try to resend");
                retry++;
            }
        }
    }
    /**
     * Constructor
     * @param localPort
     * @throws SocketException
     */
    public RELDAT_Socket(int localPort, int debug)throws SocketException{
        s = new DatagramSocket(localPort);
        seq = 0;
        ack = 0;
        state = CONNECTION_STATE.CLOSED;
        this.debug = debug;
    }
}
