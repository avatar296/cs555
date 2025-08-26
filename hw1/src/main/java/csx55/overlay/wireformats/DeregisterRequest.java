package csx55.overlay.wireformats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class DeregisterRequest implements Event {
    
    private final int type = Protocol.DEREGISTER_REQUEST;
    private String ipAddress;
    private int portNumber;
    
    public DeregisterRequest(String ipAddress, int portNumber) {
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;
    }
    
    public DeregisterRequest(byte[] marshalledBytes) throws IOException {
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
        DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));
        
        int messageType = din.readInt();
        if (messageType != Protocol.DEREGISTER_REQUEST) {
            throw new IOException("Invalid message type for DeregisterRequest");
        }
        
        this.ipAddress = din.readUTF();
        this.portNumber = din.readInt();
        
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
        dout.writeUTF(ipAddress);
        dout.writeInt(portNumber);
        dout.flush();
        
        byte[] marshalledBytes = baOutputStream.toByteArray();
        baOutputStream.close();
        dout.close();
        
        return marshalledBytes;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public int getPortNumber() {
        return portNumber;
    }
}