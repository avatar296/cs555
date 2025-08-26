package csx55.overlay.wireformats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class RegisterResponse implements Event {
    
    private final int type = Protocol.REGISTER_RESPONSE;
    private byte statusCode; // SUCCESS = 1, FAILURE = 0
    private String additionalInfo;
    
    public RegisterResponse(byte statusCode, String additionalInfo) {
        this.statusCode = statusCode;
        this.additionalInfo = additionalInfo;
    }
    
    public RegisterResponse(byte[] marshalledBytes) throws IOException {
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
        DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));
        
        int messageType = din.readInt();
        if (messageType != Protocol.REGISTER_RESPONSE) {
            throw new IOException("Invalid message type for RegisterResponse");
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