package csx55.overlay.wireformats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class DeregisterResponse implements Event {
    
    private final int type = Protocol.DEREGISTER_RESPONSE;
    private byte statusCode;
    private String additionalInfo;
    
    public DeregisterResponse(byte statusCode, String additionalInfo) {
        this.statusCode = statusCode;
        this.additionalInfo = additionalInfo;
    }
    
    public DeregisterResponse(byte[] marshalledBytes) throws IOException {
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
        DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));
        
        int messageType = din.readInt();
        if (messageType != Protocol.DEREGISTER_RESPONSE) {
            throw new IOException("Invalid message type for DeregisterResponse");
        }
        
        this.statusCode = din.readByte();
        this.additionalInfo = din.readUTF();
        
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
        dout.writeByte(statusCode);
        dout.writeUTF(additionalInfo);
        dout.flush();
        
        byte[] marshalledBytes = baOutputStream.toByteArray();
        baOutputStream.close();
        dout.close();
        
        return marshalledBytes;
    }
    
    public byte getStatusCode() {
        return statusCode;
    }
    
    public String getAdditionalInfo() {
        return additionalInfo;
    }
    
    public boolean isSuccess() {
        return statusCode == 1;
    }
}