package csx55.overlay.wireformats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class DataMessage implements Event {
    
    private final int type = Protocol.DATA_MESSAGE;
    private String sourceNodeId;
    private String sinkNodeId;
    private int payload;
    
    public DataMessage(String sourceNodeId, String sinkNodeId, int payload) {
        this.sourceNodeId = sourceNodeId;
        this.sinkNodeId = sinkNodeId;
        this.payload = payload;
    }
    
    public DataMessage(byte[] marshalledBytes) throws IOException {
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
        DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));
        
        int messageType = din.readInt();
        if (messageType != Protocol.DATA_MESSAGE) {
            throw new IOException("Invalid message type for DataMessage");
        }
        
        this.sourceNodeId = din.readUTF();
        this.sinkNodeId = din.readUTF();
        this.payload = din.readInt();
        
        baInputStream.close();
        din.close();
    }
    
    @Override
    public int getType() {
        return type;
    }
    
    @Override
    public byte[] getBytes() throws IOException {
        ByteArrayOutputStream baOutputStream = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(new BufferedOutputStream(baOutputStream));
        
        dout.writeInt(type);
        dout.writeUTF(sourceNodeId);
        dout.writeUTF(sinkNodeId);
        dout.writeInt(payload);
        dout.flush();
        
        byte[] marshalledBytes = baOutputStream.toByteArray();
        baOutputStream.close();
        dout.close();
        
        return marshalledBytes;
    }
    
    public String getSourceNodeId() {
        return sourceNodeId;
    }
    
    public String getSinkNodeId() {
        return sinkNodeId;
    }
    
    public int getPayload() {
        return payload;
    }
}