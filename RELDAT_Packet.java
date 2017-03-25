import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;


public class RELDAT_Packet implements Serializable {
    private int seq;
    private int ack;
    private int length;
    private TYPE type;
    private int wndwn;
    private byte[] data;
    private long checksum;

    //Packet type
    public enum TYPE {DATA,SYN,SYNACK,ACK,PUSH,FIN}
    public RELDAT_Packet(byte[] data, int length, int seq, int ack, TYPE type, int wndwn) {
        this.seq = seq;
        this.ack = ack;
        this.type = type;
        this.wndwn = wndwn;
        this.data = data;
        this.length = length;

    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public int getAck() {
        return ack;
    }

    public void setAck(int ack) {
        this.ack = ack;
    }

    public TYPE getType() {
        return type;
    }

    public void setType(TYPE type) {
        this.type = type;
    }

    public int getWndwn() {
        return wndwn;
    }

    public void setWndwn(int wndwn) {
        this.wndwn = wndwn;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public long getChecksum() { return checksum;}

    private byte[] getPacketByteArray() throws IOException {
        byte[] packetByteArray;
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * 4);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(seq);
        buffer.putInt(ack);
        buffer.putInt(length);
        buffer.putInt(wndwn);
        packetByteArray = buffer.array();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(output);
        outputStream.write(packetByteArray);
        outputStream.writeObject(type);
        outputStream.write(data);
        outputStream.flush();
        return output.toByteArray();

    }

    public long calculateChecksum() throws IOException {
        CRC32 checksum = new CRC32();
        checksum.update(getPacketByteArray());
        return checksum.getValue();
    }

    public void setChecksum() throws IOException {
        this.checksum = calculateChecksum();
    }
}
