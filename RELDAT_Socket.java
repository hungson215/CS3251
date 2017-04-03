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
    public enum CONNECTION_STATE {LISTEN, ESTABLISHED, CLOSED, FIN_WAIT_1,FIN_WAIT_2, CLOSE_WAIT,LAST_ACK,TIME_WAIT,OPENED}
    private int debug;
    private static RELDAT_Packet res_packet;

    public int getPort() {
        return s.getLocalPort();
    }

    public void setRecvWndwn(int recvWndwn)
    {
        this.recvWndwn = recvWndwn;
    }

    /**
     * Wrap RELDAT_packet inside a DatagramPacket
     */
    private DatagramPacket Pack(RELDAT_Packet data) throws IOException{
        //Convert an object to byte array
        data.setChecksum();
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

        while(state == CONNECTION_STATE.LISTEN) {
            byte[] buffer = new byte[MSS];
            DatagramPacket p = new DatagramPacket(buffer,buffer.length);
            s.receive(p); // Wait for connection request
            res_packet = Unpack(p);

            //If SYN request received
            if (res_packet.getType() == RELDAT_Packet.TYPE.SYN && validatePacket(res_packet)) {
                if (debug == 1) {
                    if(remoteAddr != p.getAddress()){
                        System.out.println("SYN Request Received from " + p.getAddress().toString());
                    } else {
                        System.out.println("SYNACK is lost. Resend SYNACK!");
                    }
                }
                ack = res_packet.getSeq() + res_packet.getLength();
                senderWndwn = res_packet.getWndwn();
                remoteAddr = p.getAddress();
                remotePort = p.getPort();

                //Send SYNACK
                if (debug == 1) {
                    System.out.println("Send SYNACK to " + remoteAddr.toString());
                }
                s.send(Pack(new RELDAT_Packet(new byte[1], 1, seq, ack, RELDAT_Packet.TYPE.SYNACK, recvWndwn)));
                seq += 1;

                //If ACK of SYNACK Received
            } else if(validatePacket(res_packet) && (res_packet.getType() == RELDAT_Packet.TYPE.ACK) &&  (ack == res_packet.getSeq())){
                if(debug ==1) {
                    System.out.println("ACK of SYNACK is received!");
                }
                state = CONNECTION_STATE.ESTABLISHED;
                ack += res_packet.getLength();
                res_packet = null;
                //If ACK of SYNACK is lost
            } else if(validatePacket(res_packet) && res_packet.getType() != RELDAT_Packet.TYPE.SYN){ //DATA,PUSH,FIN
                ack = res_packet.getSeq();
                state = CONNECTION_STATE.ESTABLISHED;
            }
        }
        System.out.println("---CONNECTION STATUS---");
        System.out.println("State          : " + state);
        System.out.println("LocalPort      : " + s.getLocalPort());
        System.out.println("LocalAddress   : " + Inet4Address.getLocalHost());
        System.out.println("Seq            : " + seq);
        System.out.println("Ack            : " + ack);
        System.out.println("RecvWindow     : " + recvWndwn);
        System.out.println("SenderWindow   : " + senderWndwn);
        System.out.println("Remote Address : " + remoteAddr.toString());
        System.out.println("Remote Port    : " + remotePort);
        System.out.println("-------------------------");
    }

    /**
     * Create a connection to the server
     * @param serverAddr
     * @param serverPort
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void connect(String serverAddr, int serverPort) throws IOException,ClassNotFoundException {
        state = CONNECTION_STATE.OPENED;
        byte[] buffer = new byte[1];
        this.remoteAddr = InetAddress.getByName(serverAddr);
        this.remotePort = serverPort;
        s.setSoTimeout(500);
        int retry = 0;

        //Send SYN packet
        RELDAT_Packet reldat_syn_packet = new RELDAT_Packet(buffer,1, seq,ack, RELDAT_Packet.TYPE.SYN,recvWndwn);
        reldat_syn_packet.setChecksum();
        seq+=buffer.length;
        if(debug == 1) {
            System.out.println("Send out SYN request to " + serverAddr);
        }
        s.send(Pack(reldat_syn_packet));

        while (state == CONNECTION_STATE.OPENED) {
            try {
                //Wait for SYNACK and send ACK
                buffer = new byte[MSS];
                DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                s.receive(p);
                res_packet = Unpack(p);

                //If the packet received is SYNACK
                if (validatePacket(res_packet) && res_packet.getType() == RELDAT_Packet.TYPE.SYNACK) {
                    if (debug == 1) {
                        System.out.println("SYNACK received!");
                    }
                    ack = res_packet.getSeq() + res_packet.getLength();
                    senderWndwn = res_packet.getWndwn();
                    remoteAddr = p.getAddress();
                    remotePort = p.getPort();
                    if (debug == 1) {
                        System.out.println("Send ACK of SYNACK packet!");
                    }
                    s.send(Pack(new RELDAT_Packet(new byte[1], 1, seq, ack, RELDAT_Packet.TYPE.ACK, recvWndwn)));
                    seq += 1;
                    state = CONNECTION_STATE.ESTABLISHED;
                //Else, resend SYN request
                } else {
                    if(debug == 1) {
                        System.out.println("Expect SYNACK. Received " + res_packet.getType());
                        System.out.println("Resend SYN !");
                        s.send(Pack(reldat_syn_packet));
                    }
                }
            }catch (SocketTimeoutException e){
                if(retry < 3) {
                    System.out.println("Timeout! Try to resend SYN...");
                    s.send(Pack(reldat_syn_packet));
                    retry++;
                } else {
                    System.out.println("Closing the connection");
                    state = CONNECTION_STATE.CLOSED;
                }
            }
        }
        System.out.println("---CLIENT STATUS---");
        System.out.println("State          : " + state);
        System.out.println("LocalPort      : " + s.getLocalPort());
        System.out.println("LocalAddress   : " + Inet4Address.getLocalHost());
        System.out.println("Seq            : " + seq);
        System.out.println("Ack            : " + ack);
        System.out.println("RecvWindown    : " + recvWndwn);
        System.out.println("SenderWindow   : " + senderWndwn);
        System.out.println("Remote Address : " + remoteAddr.toString());
        System.out.println("Remote Port    : " + remotePort);
        res_packet = null;
    }

    /**
     * Sending File or a String
     * @param filename
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void send(String filename) throws IOException,ClassNotFoundException {
        s.setSoTimeout(1000);
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
                    res_packet = Unpack(p);

                    if (debug == 1) {
                        System.out.println("Received ACK. Expected sending packet# " + res_packet.getAck());
                    }
                    //Check if the received packet is ACK
                    if (validatePacket(res_packet) && res_packet.getType() == RELDAT_Packet.TYPE.ACK) {
                        //Check if the received packet is expected. Then move the window forward
                        if ((ack == res_packet.getSeq()) &&
                                (res_packet.getAck() == (packets.get(0).getSeq() + packets.get(0).getLength()))) {
                            //Update ack number
                            ack += res_packet.getLength();
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
                        } else if (ack < res_packet.getSeq()) {
                            if(debug == 1) {
                                System.out.println("<------ACK packet loss detected!-------->");
                            }
                            //Update the ack number
                            ack = res_packet.getSeq() + res_packet.getLength();
                            //Need to send multiple packets
                            while (packets.get(0).getSeq() < res_packet.getAck()) {
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
                        } else if ((ack == res_packet.getSeq()) &&
                                (res_packet.getAck() == (packets.get(0).getSeq()))){
                            ack += res_packet.getLength();
                            lossDetector++;
                            if(lossDetector > 2) {
                                if(debug == 1) {
                                    System.out.println("<------Packet loss detected------->");
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
                        seq = packets.get(0).getSeq();
                        packets.clear();
                        throw new SocketException("Failed to transfer.");
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
        byte[] buffer;
        DatagramPacket p;
        boolean success = false;
        if(debug == 1) {
            System.out.println("Start receiving data....");
        }
        while(true) {
            try {
                buffer = new byte[2 * MSS];
                p = new DatagramPacket(buffer, buffer.length);
                s.receive(p);
                res_packet = Unpack(p);

                //If correct packet is received
                if (validatePacket(res_packet) && res_packet.getSeq() == ack) {
                    //Update ack number for the next packet
                    ack += res_packet.getLength();
                    //If the packet has data that need to store
                    if ((res_packet.getType() == RELDAT_Packet.TYPE.DATA) ||
                        (res_packet.getType() == RELDAT_Packet.TYPE.PUSH)) {
                        //Print debug info
                        if (debug == 1) {
                            if (res_packet.getType() == RELDAT_Packet.TYPE.FIN){
                                System.out.println("Disconnect request received!");
                            } else if (res_packet.getType() == RELDAT_Packet.TYPE.DATA){
                                System.out.println("Packet(#" + res_packet.getSeq() + ") is received");
                            }
                        }
                        //If the file is currently opened for writing. Then write the received data to the file
                        if (fos != null) {
                            if (debug == 1) {
                                System.out.println("Write packet#" + res_packet.getSeq() + " to file");
                            }
                            fos.write(res_packet.getData(), 0, res_packet.getLength());
                        //Else, add it to the string buffer
                        } else {
                            str.append(new String(res_packet.getData()));
                        }
                        //Send ACK of the received packet
                        if (debug == 1) {
                            if(res_packet.getType() == RELDAT_Packet.TYPE.DATA) {
                                System.out.println("Sending ACK for packet#" + res_packet.getSeq() + " and waiting for packet# " + ack);
                            } else {
                                System.out.println("Sending ACK for packet#" + res_packet.getSeq());
                            }
                        }
                        buffer = new byte[1];
                        s.send(Pack(new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.ACK, senderWndwn)));
                        seq += buffer.length;

                        //Decide what to do when last packet is received
                        if (res_packet.getType() == RELDAT_Packet.TYPE.PUSH) {
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
                                    s.setSoTimeout(30000);
                                    if (debug == 1) {
                                        System.out.println("File transfer request is received.");
                                    }
                                //Else, return the received message
                                } else {
                                    success = true;
                                    s.setSoTimeout(500);
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
                    } else if (res_packet.getType() == RELDAT_Packet.TYPE.FIN) {
                        if(debug == 1) {
                            System.out.println("FIN Received!");
                        }
                        if(fos != null) {
                            if(debug == 1) {
                                System.out.println("File transfer is failed because the sender requested for disconnection!");
                            }
                            try {
                                File f = new File(filename);
                                if(f.delete()) {
                                    if(debug == 1) {
                                        System.out.println("Deleted the incomplete file. Disconnecting....");
                                    }
                                } else {
                                    if(debug == 1) {
                                        System.out.println("Disconnecting....");
                                    }
                                }
                            } catch (Exception e) {
                                System.out.println("Disconnecting....");
                            }
                            fos.flush();
                            fos.close();
                            fos = null;
                        }
                        if(debug == 1) {
                            System.out.println("Send ACK of FIN packet!");
                            System.out.println("Connection state: ESTABLISHED --> CLOSE_WAIT");
                        }
                        state = CONNECTION_STATE.CLOSE_WAIT;
                        disconnect();
                        throw new SocketException("Connection is closed");
                    }
                //If a packet lost happen send ACK for the last valid packet received
                } else {
                    if(success && debug == 1) {
                        System.out.println("<--------Duplicate packet detected!-------->");
                    } else if (debug == 1) {
                        System.out.println("Wrong packet received! Expected packet#" + ack + ". Received packet#" + res_packet.getSeq());
                    }
                    //Send ACK for the last in-order packet
                    buffer = new byte[1];
                    s.send(Pack(new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.ACK, senderWndwn)));
                    seq += buffer.length;
                }
            }catch (SocketTimeoutException e) {
                if(!success && debug == 1) {
                    System.out.println("File transfer is failed!");
                    if(fos != null) {
                        fos.flush();
                        fos.close();
                    }
                    throw new SocketException("File failed to transfer");
                } else {
                    return str.toString();
                }
            }
        }
        return filename;
    }

    public void disconnect() throws IOException, ClassNotFoundException{
        RELDAT_Packet reldat_fin_packet = null;
        RELDAT_Packet reldat_ack_packet = null;
        byte[] buffer;
        int retry = 0;
        s.setSoTimeout(500);

        //Active disconnect
        if(state == CONNECTION_STATE.ESTABLISHED) {
            //Prepare FIN packet
            buffer = new byte[1];
            reldat_fin_packet = new RELDAT_Packet(buffer,buffer.length,seq,ack, RELDAT_Packet.TYPE.FIN,recvWndwn);
            seq+=buffer.length;
            state = CONNECTION_STATE.FIN_WAIT_1;
            if(debug == 1) {
                System.out.println("Send FIN packet!");
                System.out.println("Connection state: ESTABLISHED --> FIN_WAIT_1");
            }
            s.send(Pack(reldat_fin_packet));
        //Passive disconnect
        } else if(state == CONNECTION_STATE.CLOSE_WAIT){
            //Send ACK of FIN packet
            buffer = new byte[1];
            reldat_ack_packet = new RELDAT_Packet(buffer,buffer.length,seq,ack, RELDAT_Packet.TYPE.ACK,recvWndwn);
            seq+=buffer.length;
            s.send(Pack(reldat_ack_packet));

            //Send FIN
            if(debug == 1) {
                System.out.println("Send FIN packet!");
                System.out.println("Connection State: CLOSE_WAIT --> LAST_ACK");
            }
            buffer = new byte[1];
            state = CONNECTION_STATE.LAST_ACK;
            reldat_fin_packet = new RELDAT_Packet(buffer,buffer.length,seq,ack, RELDAT_Packet.TYPE.FIN,recvWndwn);
            seq+= buffer.length;
            s.send(Pack(reldat_fin_packet));
        }
        while(state != CONNECTION_STATE.CLOSED) {
            try {
                //Wait for ACK of FIN
                buffer = new byte[MSS];
                DatagramPacket res = new DatagramPacket(buffer, buffer.length);
                s.receive(res);
                res_packet = Unpack(res);

                if (validatePacket(res_packet) && res_packet.getSeq() == ack) {
                    //If ACK of FIN is received
                    retry = 0;
                    ack += res_packet.getLength();
                    if (res_packet.getType() == RELDAT_Packet.TYPE.ACK) {
                        if(debug == 1) {
                            System.out.println("ACK of FIN received!");
                        }
                        if (state == CONNECTION_STATE.LAST_ACK) {
                            if(debug == 1) {
                                System.out.println("Connection State: LAST_ACK --> CLOSED");
                                System.out.println("-----------------------------------");
                            }
                            state = CONNECTION_STATE.CLOSED; //--> End loop
                        } else if (state == CONNECTION_STATE.FIN_WAIT_1) {
                            if(debug == 1) {
                                System.out.println("Connection State: FIN_WAIT_1 --> FIN_WAIT_2");
                            }
                            state = CONNECTION_STATE.FIN_WAIT_2;
                        }
                    } else if (res_packet.getType() == RELDAT_Packet.TYPE.FIN) {
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
                            reldat_ack_packet = new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.ACK, recvWndwn);
                            seq += buffer.length;
                            s.send(Pack(reldat_ack_packet));
                        }
                    }
                } else if(validatePacket(res_packet) && res_packet.getType() == RELDAT_Packet.TYPE.FIN) {
                    //If ACK of FIN is lost, but received FIN.
                    if(state == CONNECTION_STATE.FIN_WAIT_1) {
                        if(debug == 1) {
                            System.out.println("<---Expect ACK of FIN. Received FIN--->");
                            System.out.println("Connection State: FIN_WAIT_1 --> FIN_WAIT_2");
                            System.out.println("Send ACK of FIN!");
                            System.out.println("Connection State: FIN_WAIT_2 --> TIME_WAIT");
                        }
                        ack = res_packet.getSeq() + res_packet.getLength();
                        buffer = new byte[1];
                        reldat_ack_packet = new RELDAT_Packet(buffer, buffer.length, seq, ack, RELDAT_Packet.TYPE.ACK, recvWndwn);
                        seq += buffer.length;
                        s.send(Pack(reldat_ack_packet));

                        state = CONNECTION_STATE.TIME_WAIT;
                    } else {
                        if(debug == 1) {
                            System.out.println("FIN is lost");
                        }
                        s.send(Pack(reldat_ack_packet));
                        s.send(Pack(reldat_fin_packet));
                    }
                }
            } catch (SocketTimeoutException e) {
                if(state == CONNECTION_STATE.TIME_WAIT) {
                    if (debug == 1) {
                        System.out.println("Time's up! Disconnecting.....");
                        System.out.println("Connection State: TIME_WAIT -> CLOSED");
                        System.out.println("-----------------------------------");
                    }
                    state = CONNECTION_STATE.CLOSED;
                    return;
                } else if(retry > 3) {
                    if(debug == 1) {
                        System.out.println("No response from the client. The connection will be closed!");
                    }
                    state = CONNECTION_STATE.CLOSED;
                    return;
                } else {
                    //Resend FIN packet
                    if(debug == 1) {
                        System.out.println("Timeout! Resend FIN!!");
                    }
                    s.send(Pack(reldat_fin_packet));
                    retry++;
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
        res_packet = null;
    }
}
