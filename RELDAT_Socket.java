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
    public enum CONNECTION_STATE {LISTEN, ESTABLISHED, CLOSED, FIN_WAIT_1,FIN_WAIT_2, CLOSING, CLOSE_WAIT,LAST_ACK}
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
    private ArrayList<RELDAT_Packet> Packetize(byte[] data, int length) throws IOException{
        ArrayList<RELDAT_Packet> packets = new ArrayList<>();
        byte[] buffer;
        //If data size less than MSS then put everything into 1 packet
        if(length  < MSS) {
            buffer = new byte[length];
            System.arraycopy(data,0,buffer,0,length);
            RELDAT_Packet reldat_packet = new RELDAT_Packet(buffer,length,seq,ack, RELDAT_Packet.TYPE.DATA,recvWndwn);
            packets.add(reldat_packet);
            seq+= length;
            //Else, split data into multiple packets
        } else {
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
                    reldat_packet = new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.DATA,recvWndwn);
                    seq += length - i;
                }
                packets.add(reldat_packet);
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
        s.setSoTimeout(0);
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
        seq+=1;
        DatagramPacket p = Pack(reldat_packet);
        s.setSoTimeout(3000);
        int retry = 0;
        while (true) {
            try {
                if(debug == 1) {
                    System.out.println("Send out SYN request to " + serverAddr);
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

    /**
     * Sending File or a String
     * @param filename
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void send(String filename) throws IOException,ClassNotFoundException {
        s.setSoTimeout(2000);
        FileInputStream fis ;
        byte[] sendData = new byte[recvWndwn];
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
            while((byteCount = fis.read(sendData,0,recvWndwn))!= -1) {
                packets.addAll(Packetize(sendData,byteCount));
            }
            fis.close();
            //If the file does not exist. Treat it as a String
        }catch (FileNotFoundException e) {
            packets.addAll(Packetize(filename.getBytes(),filename.length()));
        }

        //set the last packet's type as PUSH
        packets.get(packets.size()-1).setType(RELDAT_Packet.TYPE.PUSH);
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
            while (byteCount < recvWndwn && i < packets.size()) {
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
                    if (reldat_recvpacket.getType() == RELDAT_Packet.TYPE.ACK) {
                        //Check if the received packet is expected. Then move the window forward
                        if ((ack == reldat_recvpacket.getSeq()) &&
                                (reldat_recvpacket.getAck() == (packets.get(0).getSeq() + packets.get(0).getLength()))) {
                            //Update ack number
                            ack += reldat_recvpacket.getLength();
                            //Remove the first packet as it is sent successfully
                            packets.remove(0);
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
                                System.out.println("ACK packet loss detected.");
                            }
                            //Update the ack number
                            ack = reldat_recvpacket.getSeq() + reldat_recvpacket.getLength();
                            //Need to send multiple packets
                            while (true) {
                                //Remove the first packet
                                packets.remove(0);
                                //Check if there is any packet left
                                if (index < packets.size()) {
                                    RELDAT_Packet reldat_packet = packets.get(0);
                                    if (reldat_packet.getSeq() <= reldat_recvpacket.getAck()) {
                                        if(debug == 1) {
                                            System.out.println("Send packet#" + packets.get(index).getSeq());
                                        }
                                        s.send(Pack(packets.get(index)));
                                    } else {
                                        break;
                                    }
                                } else {
                                    if (debug == 1) {
                                        System.out.println("There's no new packet to send");
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
                    if(retry > 3) {
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
                        System.out.println("Packet#"+ reldat_packet.getSeq() +" received");
                    }
                    if (fos != null) {
                        if(debug== 1) {
                            System.out.println("Write packet#" + reldat_packet.getSeq() + " to file");
                        }
                        fos.write(reldat_packet.getData(), 0, reldat_packet.getLength());
                    }
                    ack += reldat_packet.getLength();
                    buffer = new byte[1];
                    RELDAT_Packet reldat_ackpacket = new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.ACK, senderWndwn);
                    seq += buffer.length;
                    p = Pack(reldat_ackpacket);

                    if (debug == 1) {
                        System.out.println("Sending ACK for packet#"+ reldat_packet.getSeq()+" and waiting for packet# " + ack);
                    }
                    s.send(p);
                    if (reldat_packet.getType() == RELDAT_Packet.TYPE.PUSH) {
                        if (debug == 1) {
                            System.out.println("Received All Data");
                        }
                        if (filename.isEmpty()) {
                            String init = new String(reldat_packet.getData());
                            if (init.startsWith("INIT_FILE_TRANSFER:")) {
                                int i = init.lastIndexOf(':');
                                int j = init.lastIndexOf('.');
                                filename = init.substring(i + 1, j) + "_received" + init.substring(j);
                                fos = new FileOutputStream(filename);
                                if(debug==1) {
                                    System.out.println("File transfer request received.");
                                }
                            } else { //not a file transfer request
                                return init;
                            }
                            continue;
                        }
                        if (fos != null) {
                            if (debug == 1) {
                                System.out.println("Close the file");
                            }
                            fos.flush();
                            fos.close();
                        }
                        break;
                    }
                    //If the sender request for disconnecting. Initiate disconnecting sequence
                } else if (reldat_packet.getType() == RELDAT_Packet.TYPE.FIN) {
                    if(debug == 1) {
                        System.out.println("Disconnecting request received!");
                    }
                    if(state == CONNECTION_STATE.ESTABLISHED) {
                        if(debug == 1) {
                            System.out.println("Connection State: ESTABLISHED -> CLOSE_WAIT");
                        }
                        state = CONNECTION_STATE.CLOSE_WAIT;
                        //Prepare ACK for FIN
                        ack += reldat_packet.getLength();
                        buffer = new byte[1];
                        RELDAT_Packet reldat_ackpacket = new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.ACK, senderWndwn);
                        seq += buffer.length;
                        //Prepare FIN
                        buffer = new byte[1];
                        RELDAT_Packet reldat_finpacket = new RELDAT_Packet(buffer,buffer.length,seq,ack, RELDAT_Packet.TYPE.FIN,senderWndwn);
                        seq += buffer.length;

                        DatagramPacket ackpacket = Pack(reldat_ackpacket);
                        DatagramPacket finpacket = Pack(reldat_finpacket);
                        s.setSoTimeout(2000);
                        int retry = 0;
                        while (true) {
                            try {
                                if(debug == 1) {
                                    System.out.println("Send ACK and FIN!");
                                }
                                //Send both ACK for FIN and FIN
                                s.send(ackpacket);
                                s.send(finpacket);
                                //Wait for ACK
                                buffer = new byte[MSS];
                                p = new DatagramPacket(buffer, buffer.length);
                                s.receive(p);
                                RELDAT_Packet res = Unpack(p);
                                if ((res.getType() == RELDAT_Packet.TYPE.ACK) &&
                                        (res.getAck() == seq)) {
                                    if(debug == 1) {
                                        System.out.println("ACK Received!");
                                        System.out.println("Connection State: CLOSE_WAIT -> CLOSED");
                                    }
                                    state = CONNECTION_STATE.CLOSED;
                                    if(debug== 1) {
                                        System.out.println("Connection is closed!");
                                    }
                                    throw new SocketException("Connection is closed!");
                                }
                            }catch (SocketTimeoutException e) {
                                System.out.println("Time out. Try to resend");
                                retry++;
                                if(retry > 2) {
                                    System.out.println("No responds from the receiver. Connection will be closed");
                                    state = CONNECTION_STATE.CLOSED;
                                    return null;
                                }
                            }
                        }
                    }
                }
                //If a packet lost happen send ACK for the last valid packet received
            } else {
                if(debug == 1) {
                    System.out.println("Wrong packet received! Expected packet#"+ack+". Received packet#" + reldat_packet.getSeq());
                }
                //Send ACK for the last in-order packet
                buffer = new byte[1];
                RELDAT_Packet reldat_ackpacket = new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.ACK, senderWndwn);
                seq += buffer.length;
                p = Pack(reldat_ackpacket);
                s.send(p);
            }
        }
        return null;
    }
    public void disconnect() throws IOException, ClassNotFoundException{
        //Prepare FIN packet
        byte[] buffer = new byte[1];
        RELDAT_Packet reldat_packet = new RELDAT_Packet(buffer,buffer.length,seq,ack, RELDAT_Packet.TYPE.FIN,recvWndwn);
        seq+=buffer.length;
        DatagramPacket p = Pack(reldat_packet);
        s.setSoTimeout(2000);
        int retry = 0;
        while(true) {
            try {
                if(debug == 1) {
                    System.out.println("Send FIN request");
                }
                s.send(p);
                //Wait for ACK
                if(state == CONNECTION_STATE.ESTABLISHED) {
                    if(debug == 1) {
                        System.out.println("Connection State: ESTABLISHED -> FIN_WAIT_1");
                    }
                    state = CONNECTION_STATE.FIN_WAIT_1;
                }
                while(true) {
                    try {
                        buffer = new byte[MSS];
                        DatagramPacket res = new DatagramPacket(buffer, buffer.length);
                        s.receive(res);
                        RELDAT_Packet reldat_res = Unpack(res);
                        if (reldat_res.getAck() == seq) {
                            //If ACK is received
                            ack += reldat_res.getLength();
                            if (reldat_res.getType() == RELDAT_Packet.TYPE.ACK) {
                                if(debug == 1) {
                                    System.out.println("ACK received!");
                                }
                                if (state == CONNECTION_STATE.FIN_WAIT_1) {
                                    if(debug == 1) {
                                        System.out.println("Connection State: FIN_WAIT_1 -> FIN_WAIT_2");
                                    }
                                    state = CONNECTION_STATE.FIN_WAIT_2;
                                } else if (state == CONNECTION_STATE.CLOSING) {
                                    if(debug == 1) {
                                        System.out.println("Connection State: CLOSING -> CLOSED");
                                    }
                                    state = CONNECTION_STATE.CLOSED;
                                    return;
                                }
                            } else if (reldat_res.getType() == RELDAT_Packet.TYPE.FIN) {
                                if(debug == 1) {
                                    System.out.println("FIN received!");
                                    System.out.println("Send ACK");
                                }
                                //Send ack for this fin
                                buffer = new byte[1];
                                reldat_packet = new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.ACK, recvWndwn);
                                seq += buffer.length;
                                p = Pack(reldat_packet);
                                s.send(p);
                                //set the state of the connection
                                if (state == CONNECTION_STATE.FIN_WAIT_2) {
                                    if(debug == 1) {
                                        System.out.println("Connection State: FIN_WAIT_2 -> CLOSED");
                                    }
                                    state = CONNECTION_STATE.CLOSED;
                                    return;
                                } else if (state == CONNECTION_STATE.FIN_WAIT_1) {
                                    if(debug == 1) {
                                        System.out.println("Connection State: FIN_WAIT_1 -> CLOSING");
                                    }
                                    state = CONNECTION_STATE.CLOSING;
                                }
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        if(state == CONNECTION_STATE.FIN_WAIT_2) {
                            if(debug == 1) {
                                System.out.println("Time out! Connection State: FIN_WAIT_2 -> CLOSED");
                            }
                            state = CONNECTION_STATE.CLOSED;
                            return;
                        } else {
                            throw new SocketTimeoutException(e.getMessage());
                        }
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
