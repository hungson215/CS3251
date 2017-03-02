import java.io.Serializable;


public class RELDAT_Packet implements Serializable {
    private int seq;
    private int ack;
    private int length;
    private TYPE type;
    private int wndwn;
    private byte[] data;

    //Packet type
    public enum TYPE {DATA,SYN,SYNACK,ACK}
    public RELDAT_Packet(byte[] data, int seq, int ack, TYPE type, int wndwn) {
        this.seq = seq;
        this.ack = ack;
        this.type = type;
        this.wndwn = wndwn;
        this.data = data;
        this.length = data.length;
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
}
