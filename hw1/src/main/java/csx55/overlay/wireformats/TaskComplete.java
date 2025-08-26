package csx55.overlay.wireformats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class TaskComplete implements Event {
    
    private final int type = Protocol.TASK_COMPLETE;
    private String nodeIpAddress;
    private int nodePortNumber;
    
    public TaskComplete(String nodeIpAddress, int nodePortNumber) {
        this.nodeIpAddress = nodeIpAddress;
        this.nodePortNumber = nodePortNumber;
    }
    
    public TaskComplete(byte[] marshalledBytes) throws IOException {
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
        DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));
        
        int messageType = din.readInt();
        if (messageType != Protocol.TASK_COMPLETE) {
            throw new IOException("Invalid message type for TaskComplete");
        }
        
        this.nodeIpAddress = din.readUTF();
        this.nodePortNumber = din.readInt();
        
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
        dout.writeUTF(nodeIpAddress);
        dout.writeInt(nodePortNumber);
        dout.flush();
        
        byte[] marshalledBytes = baOutputStream.toByteArray();
        baOutputStream.close();
        dout.close();
        
        return marshalledBytes;
    }
    
    public String getNodeIpAddress() {
        return nodeIpAddress;
    }
    
    public int getNodePortNumber() {
        return nodePortNumber;
    }
    
    public String getNodeId() {
        return nodeIpAddress + ":" + nodePortNumber;
    }
}