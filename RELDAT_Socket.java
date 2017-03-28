import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;


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
    public enum CONNECTION_STATE {LISTEN, ESTABLISHED, CLOSED, FIN_WAIT_1,FIN_WAIT_2, CLOSING, CLOSE_WAIT,LAST_ACK,TIME_WAIT}
    private byte[] receivedBuffer; //Receiver's Buffer
    private int debug;

    public int getPort() {
        return s.getLocalPort();
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
     * @param data,length
     * @return
     */
    private ArrayList<RELDAT_Packet> Packetize(byte[] data, int length) throws IOException {
        ArrayList<RELDAT_Packet> packets = new ArrayList<>();
        byte[] buffer;
        int minsize = (senderWndwn >= MSS) ? MSS : senderWndwn;
        //If data size less than minsize then put everything into 1 packet
        if(length  < minsize) {
            buffer = new byte[length];
            System.arraycopy(data,0,buffer,0,length);
            RELDAT_Packet reldat_packet = new RELDAT_Packet(buffer,length,seq,ack, RELDAT_Packet.TYPE.DATA,recvWndwn);
            reldat_packet.setChecksum();
            packets.add(reldat_packet);
            seq+= length;
            //Else, split data into multiple packets
        } else {
            int i = 0;
            while(i < length) {
                RELDAT_Packet reldat_packet;
                if(i + minsize <= length) {
                    buffer = new byte[minsize];
                    System.arraycopy(data, i, buffer, 0, minsize);
                    reldat_packet = new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.DATA, recvWndwn);
                    reldat_packet.setChecksum();
                    seq += minsize;
                } else{
                    buffer = new byte[length - i];
                    System.arraycopy(data, i, buffer, 0, length - i);
                    reldat_packet = new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.DATA,recvWndwn);
                    reldat_packet.setChecksum();
                    seq += length - i;
                }
                packets.add(reldat_packet);
                i+= minsize;
            }
        }
        return packets;
    }

    /**
     * checking to see if packet is not corrupt
     * @param packetV
     * @return if packet is corrupt or not
     * @throws IOException
     */
    private boolean validatePacket(RELDAT_Packet packetV) throws IOException {
        long checksumVal = packetV.calculateChecksum();
        return checksumVal == packetV.getChecksum();
    }

    /**
     * Establish a reliable connection using 3-way handshake
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void accept() throws IOException, ClassNotFoundException {
        s.setSoTimeout(0);
        state = CONNECTION_STATE.LISTEN;
        byte[] buffer = new byte[MSS];
        DatagramPacket p = new DatagramPacket(buffer,buffer.length);
        s.receive(p); // Wait for connection request
        RELDAT_Packet reldat_packet = Unpack(p);
        //If SYN request received
        if (reldat_packet.getType() == RELDAT_Packet.TYPE.SYN && validatePacket(reldat_packet)) {
            if(debug == 1) {
                System.out.println("SYN Request Received from " + p.getAddress().toString());
            }
            ack = reldat_packet.getSeq() + reldat_packet.getLength();
            senderWndwn = reldat_packet.getWndwn();
            remoteAddr = p.getAddress();
            remotePort = p.getPort();

            reldat_packet = new RELDAT_Packet(new byte[1],1, seq, ack, RELDAT_Packet.TYPE.SYNACK, recvWndwn);
            reldat_packet.setChecksum();
            if(debug == 1) {
                System.out.println("Send SYNACK to " + remoteAddr.toString());
            }
            seq += 1;
            s.setSoTimeout(5000); // Set timout for 5s
            int retry = 0;
            //Wait and resend SYNACK when needed
            while(true) {
                try {
                    s.send(Pack(reldat_packet));
                    buffer = new byte[MSS];
                    p = new DatagramPacket(buffer, buffer.length);
                    while(true) {
                        s.receive(p);
                        RELDAT_Packet res = Unpack(p);
                        //If ACK of SYNACK received
                        if ((res.getType() == RELDAT_Packet.TYPE.ACK) && validatePacket(res) && (ack == res.getSeq())) {
                            if(debug ==1) {
                                System.out.println("ACK of SYNACK Received. Connection is established!");
                            }
                            state = CONNECTION_STATE.ESTABLISHED;
                            ack += res.getLength();
                            break;
                        }
                    }
                    break;
                }catch (SocketTimeoutException e){
                    System.out.println("Timeout. Try to resend SYNACK...");
                    if(retry > 5) {
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

    /**
     * Create a connection to the server
     * @param serverAddr
     * @param serverPort
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void connect(String serverAddr, int serverPort) throws IOException,ClassNotFoundException {
        byte[] buffer = new byte[1];
        this.remoteAddr = InetAddress.getByName(serverAddr);
        this.remotePort = serverPort;
        RELDAT_Packet reldat_packet = new RELDAT_Packet(buffer,1, seq,ack, RELDAT_Packet.TYPE.SYN,recvWndwn);
        reldat_packet.setChecksum();
        seq+=1;
        s.setSoTimeout(3000);
        DatagramPacket p;
        int retry = 0;
        while (true) {
            try {
                if(debug == 1) {
                    System.out.println("Send out SYN request to " + serverAddr);
                }
                s.send(Pack(reldat_packet));
                //Wait for SYNACK and send ACK
                while(true) {
                    try {
                        buffer = new byte[MSS];
                        p = new DatagramPacket(buffer, buffer.length);
                        s.receive(p);
                        RELDAT_Packet res = Unpack(p);
                        if (res.getType() == RELDAT_Packet.TYPE.SYNACK && validatePacket(res)) {
                            if (debug == 1) {
                                System.out.println("SYNACK received!");
                            }
                            ack = res.getSeq() + res.getLength();
                            senderWndwn = res.getWndwn();
                            remoteAddr = p.getAddress();
                            remotePort = p.getPort();
                        } else {
                            if(debug == 1) {
                                System.out.println("Expect SYNACK. Received " + res.getType());
                                System.out.println("Resend SYN");
                            }
                            break;
                        }
                        reldat_packet = new RELDAT_Packet(new byte[1], 1, seq, ack, RELDAT_Packet.TYPE.ACK, recvWndwn);
                        reldat_packet.setChecksum();
                        seq += 1;
                        if (debug == 1) {
                            System.out.println("Send ACK of SYNACK");
                        }
                        s.send(Pack(reldat_packet));
                        state = CONNECTION_STATE.ESTABLISHED;
                    } catch (SocketTimeoutException e) {
                        if(state == CONNECTION_STATE.ESTABLISHED) {
                            return;
                        } else {
                            throw new SocketTimeoutException(e.getMessage());
                        }
                    }
                }
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

    /**
     * Sending File or a String
     * @param filename
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void send(String filename) throws IOException,ClassNotFoundException {
        s.setSoTimeout(2000);
        FileInputStream fis ;
        byte[] sendData = new byte[MSS];
        int byteCount;
        ArrayList<RELDAT_Packet> packets = new ArrayList<>();
        int retry;
        //Check if the file exist
        try{
            fis = new FileInputStream(filename);
            //Initiate file transfer
            String initStr = "INIT_FILE_TRANSFER:" + filename;
            if(debug == 1) {
                System.out.println("Sending file transfer request");
            }
            send(initStr);
            //Begin file transfer
            if(debug == 1) {
                System.out.println("Prepare data to transfer...");
            }
            //Prepare data to transfer
            while((byteCount = fis.read(sendData,0,MSS))!= -1) {
                packets.addAll(Packetize(sendData,byteCount));
            }
            fis.close();
            //If the file does not exist. Treat it as a String
        }catch (FileNotFoundException e) {
            packets.addAll(Packetize(filename.getBytes(),filename.length()));
        }

        //set the last packet's type as PUSH
        packets.get(packets.size()-1).setType(RELDAT_Packet.TYPE.PUSH);
        packets.get(packets.size()-1).setChecksum();
        if(debug == 1) {
            System.out.println("Total packets need to send: " + packets.size());
            System.out.println("Start sending data...");
        }
        //Start sending data
        retry = 0;
        while(!packets.isEmpty()) {
            byteCount = 0;
            int i = 0;
            //Sending first window
            while (byteCount < senderWndwn && i < packets.size()) {
                if(debug == 1) {
                    System.out.println("Send packet#" + packets.get(i).getSeq());
                }
                s.send(Pack(packets.get(i)));
                byteCount += packets.get(i).getLength();
                i++;
            }
            int index = i - 1;
            //int count = 0;
            int lossDetector = 0;
            //Start waiting for ACK and sending new packets
            while (!packets.isEmpty()) {
                try {
                    byte[] buffer = new byte[MSS];
                    DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                    s.receive(p);
                    //Packet received
                    RELDAT_Packet reldat_recvpacket = Unpack(p);
                    if (debug == 1) {
                        System.out.println("Received ACK. Next expected packet# " + reldat_recvpacket.getAck());
                    }
                    //Check if the received packet is ACK
                    if (reldat_recvpacket.getType() == RELDAT_Packet.TYPE.ACK && validatePacket(reldat_recvpacket)) {
                        //Check if the received packet is expected. Then move the window forward
                        if ((ack == reldat_recvpacket.getSeq()) &&
                                (reldat_recvpacket.getAck() == (packets.get(0).getSeq() + packets.get(0).getLength()))) {
                            //Update ack number
                            ack += reldat_recvpacket.getLength();
                            //Remove the first packet as it is sent successfully
                            packets.remove(0);
                            //Reset retry variable
                            if(retry > 0) {
                                retry = 0;
                            }
                            //Send the new packet if there is any
                            if (index < packets.size()) {
                                if(debug == 1) {
                                    System.out.println("Send packet#" + packets.get(index).getSeq());
                                }
                                s.send(Pack(packets.get(index)));
                            } else if (debug == 1) {
                                System.out.println("No new packet to send");
                            }
                            //If some acks are lost, assume sent data are already received based on the last ack received
                        } else if (ack < reldat_recvpacket.getSeq()) {
                            if(debug == 1) {
                                System.out.println("ACK packet loss detected!");
                            }
                            //Update the ack number
                            ack = reldat_recvpacket.getSeq() + reldat_recvpacket.getLength();
                            //Need to send multiple packets
                            while (packets.get(0).getSeq() < reldat_recvpacket.getAck()) {
                                //Remove the first packet
                                packets.remove(0);
                                //Check if there is any packet left
                                if (index < packets.size()) {
                                    RELDAT_Packet reldat_packet = packets.get(0);
                                    if(debug == 1) {
                                        System.out.println("Send packet#" + packets.get(index).getSeq());
                                    }
                                        s.send(Pack(packets.get(index)));
                                } else {
                                    if (debug == 1) {
                                        System.out.println("No new packet to send");
                                    }
                                    break;
                                }
                            }
                            //If there's a packet loss. Wait until 3 duplicated acks are received to start retransmitting
                            // or time out and retransmit
                        } else if ((ack == reldat_recvpacket.getSeq()) &&
                                (reldat_recvpacket.getAck() == (packets.get(0).getSeq()))){
                            ack += reldat_recvpacket.getLength();
                            lossDetector++;
                            if(lossDetector > 2) {
                                if(debug == 1) {
                                    System.out.println("Packet loss detected");
                                }
                                break;
                            }
                        }
                    }
                    //Time out when an ack is lost. Resending all the packets in the recvWindow
                } catch (SocketTimeoutException e) {
                    if(retry > 5) {
                        if(debug == 1) {
                            System.out.println("Failed to transfer the file");
                        }
                        return;
                    }
                    if(debug == 1) {
                        System.out.println("Time out. Try to resend");
                    }
                    retry++;
                    break;
                }
            }
        }
        if(debug == 1) {
            System.out.println("Transfer completed!");
        }
    }

    /**
     * Receiving data
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public String receive() throws IOException,ClassNotFoundException{
        s.setSoTimeout(0);
        String filename ="";
        FileOutputStream fos = null;
        StringBuilder str = new StringBuilder();
        if(debug == 1) {
            System.out.println("Start receiving data....");
        }
        while(true) {
            try {
                byte[] buffer = new byte[2 * MSS];
                DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                s.receive(p);
                RELDAT_Packet reldat_packet = Unpack(p);
                //If correct packet is received
                if (reldat_packet.getSeq() == ack && validatePacket(reldat_packet)) {
                    //Update ack number for the next packet
                    ack += reldat_packet.getLength();
                    //If the packet has data that need to store
                    if ((reldat_packet.getType() == RELDAT_Packet.TYPE.DATA) ||
                        (reldat_packet.getType() == RELDAT_Packet.TYPE.PUSH)) {
                        //Print debug info
                        if (debug == 1) {
                            if (reldat_packet.getType() == RELDAT_Packet.TYPE.FIN){
                                System.out.println("Disconnect request received!");
                            } else if (reldat_packet.getType() == RELDAT_Packet.TYPE.DATA){
                                System.out.println("Packet(#" + reldat_packet.getSeq() + ") is received");
                            }
                        }
                        //If the file is currently opened for writing. Then write the received data to the file
                        if (fos != null) {
                            if (debug == 1) {
                                System.out.println("Write packet#" + reldat_packet.getSeq() + " to file");
                            }
                            fos.write(reldat_packet.getData(), 0, reldat_packet.getLength());
                        //Else, add it to the string buffer
                        } else {
                            str.append(new String(reldat_packet.getData()));
                        }
                        //Send ACK of the received packet
                        if (debug == 1) {
                            if(reldat_packet.getType() == RELDAT_Packet.TYPE.DATA) {
                                System.out.println("Sending ACK for packet#" + reldat_packet.getSeq() + " and waiting for packet# " + ack);
                            } else {
                                System.out.println("Sending ACK for packet#" + reldat_packet.getSeq());
                            }
                        }
                        buffer = new byte[1];
                        RELDAT_Packet ackPacket = new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.ACK, senderWndwn);
                        ackPacket.setChecksum();
                        s.send(Pack(ackPacket));
                        seq += buffer.length;

                        //Decide what to do when last packet is received
                        if (reldat_packet.getType() == RELDAT_Packet.TYPE.PUSH) {
                            if (debug == 1) {
                                System.out.println("All Data are received!");
                            }
                            if (filename.isEmpty()) {
                                String msg = str.toString();
                                //If file transfer request is received, create the file for writing
                                if (msg.startsWith("INIT_FILE_TRANSFER:")) {
                                    int i = msg.lastIndexOf(':');
                                    int j = msg.lastIndexOf('.');
                                    filename = msg.substring(i + 1, j) + "_received" + msg.substring(j);
                                    fos = new FileOutputStream(filename);
                                    s.setSoTimeout(10000);
                                    if (debug == 1) {
                                        System.out.println("File transfer request is received.");
                                    }
                                //Else, return the received message
                                }
                                continue;
                            //If the file is already opened for writing, close it
                            } else if (fos != null) {
                                if (debug == 1) {
                                    System.out.println("File Transfer is completed. Close the file");
                                }
                                s.setSoTimeout(0);
                                fos.flush();
                                fos.close();
                                fos = null;
                            }
                            break; // break out of the infinite loop
                        }
                    //If the sender request for disconnecting. Initiate disconnecting sequence
                    } else if (reldat_packet.getType() == RELDAT_Packet.TYPE.FIN) {
                        if (state == CONNECTION_STATE.ESTABLISHED) {
                            state = CONNECTION_STATE.CLOSE_WAIT;
                            disconnect();
                            throw new SocketException("Connection is closed");
                        }
                    }
                //If a packet lost happen send ACK for the last valid packet received
                } else {
                    if (debug == 1) {
                        System.out.println("Wrong packet received! Expected packet#" + ack + ". Received packet#" + reldat_packet.getSeq());
                    }
                    //Send ACK for the last in-order packet
                    buffer = new byte[1];
                    RELDAT_Packet ackPacket = new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.ACK, senderWndwn);
                    ackPacket.setChecksum();
                    s.send(Pack(ackPacket));
                    seq += buffer.length;
                }
            }catch (SocketTimeoutException e) {
                if(debug == 1) {
                    System.out.println("File transfer is failed!");
                }
                if(fos != null) {
                    fos.flush();
                    fos.close();
                }
                break;
            }
        }
        return filename;
    }

    public void disconnect() throws IOException, ClassNotFoundException{
        RELDAT_Packet reldat_packet = null;
        s.setSoTimeout(2000);
        byte[] buffer;
        //Active disconnect
        if(state == CONNECTION_STATE.ESTABLISHED) {
            //Prepare FIN packet
            buffer = new byte[1];
            reldat_packet = new RELDAT_Packet(buffer,buffer.length,seq,ack, RELDAT_Packet.TYPE.FIN,recvWndwn);
            reldat_packet.setChecksum();
            seq+=buffer.length;
            state = CONNECTION_STATE.FIN_WAIT_1;
            if(debug == 1) {
                System.out.println("Send FIN packet!");
                System.out.println("Connection state: ESTABLISHED --> FIN_WAIT_1");
            }
            s.send(Pack(reldat_packet));
        //Passive disconnect
        } else if(state == CONNECTION_STATE.CLOSE_WAIT){
            //Send ACK of FIN packet
            buffer = new byte[1];
            reldat_packet = new RELDAT_Packet(buffer,buffer.length,seq,ack, RELDAT_Packet.TYPE.ACK,recvWndwn);
            reldat_packet.setChecksum();
            seq+=buffer.length;
            if(debug == 1) {
                System.out.println("Send ACK of FIN packet!");
                System.out.println("Connection state: ESTABLISHED --> CLOSE_WAIT");
            }
            s.send(Pack(reldat_packet));

            //Send FIN
            buffer = new byte[1];
            RELDAT_Packet finPacket2 =  new RELDAT_Packet(buffer,buffer.length,seq,ack, RELDAT_Packet.TYPE.FIN,recvWndwn);
            finPacket2.setChecksum();
            s.send(Pack(finPacket2));
            state = CONNECTION_STATE.LAST_ACK;
            if(debug == 1) {
                System.out.println("Send FIN packet!");
                System.out.println("Connection State: CLOSE_WAIT --> LAST_ACK");
            }
            s.send(Pack(reldat_packet));
        }
        while(true) {
            try {
                //Wait for ACK of FIN
                buffer = new byte[MSS];
                DatagramPacket res = new DatagramPacket(buffer, buffer.length);
                s.receive(res);
                RELDAT_Packet reldat_res = Unpack(res);
                if (reldat_res.getSeq() == ack && validatePacket(reldat_res)) {
                    //If ACK of FIN is received
                    ack += reldat_res.getLength();
                    if (reldat_res.getType() == RELDAT_Packet.TYPE.ACK) {
                        if(debug == 1) {
                            System.out.println("ACK of FIN received!");
                        }
                        if (state == CONNECTION_STATE.LAST_ACK) {
                            if(debug == 1) {
                                System.out.println("Connection State: LAST_ACK --> CLOSED");
                            }
                            state = CONNECTION_STATE.CLOSED;
                            return;
                        } else if (state == CONNECTION_STATE.FIN_WAIT_1) {
                            if(debug == 1) {
                                System.out.println("Connection State: FIN_WAIT_1 --> FIN_WAIT_2");
                            }
                            state = CONNECTION_STATE.FIN_WAIT_2;
                            continue;
                        }
                    } else if (reldat_res.getType() == RELDAT_Packet.TYPE.FIN) {
                        if(debug == 1) {
                            System.out.println("FIN received!");
                        }
                        if(state == CONNECTION_STATE.FIN_WAIT_2) {
                            if(debug == 1) {
                                System.out.println("Connection State: FIN_WAIT_2 --> TIME_WAIT");
                            }
                            state = CONNECTION_STATE.TIME_WAIT;
                            //Send ack for this fin
                            buffer = new byte[1];
                            reldat_packet = new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.ACK, recvWndwn);
                            reldat_packet.setChecksum();
                            seq += buffer.length;
                            s.send(Pack(reldat_packet));
                        } else {
                            s.send(Pack(reldat_packet));
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                if(state == CONNECTION_STATE.TIME_WAIT) {
                    if (debug == 1) {
                        System.out.println("Connection State: TIME_WAIT -> CLOSED");
                    }
                    state = CONNECTION_STATE.CLOSED;
                    return;
                } else {
                    buffer = new byte[1];
                    RELDAT_Packet finPacket = new RELDAT_Packet(buffer,buffer.length,seq,ack, RELDAT_Packet.TYPE.FIN,recvWndwn);
                    finPacket.setChecksum();
                    s.send(Pack(finPacket));
                }
            }
        }

    }

    public CONNECTION_STATE getState() {
        return state;
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
