package csx55.overlay.wireformats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Response message sent by the registry after processing a registration request.
 * Contains a status code indicating success or failure and additional
 * information about the registration result.
 * 
 * Wire format:
 * - int: message type (REGISTER_RESPONSE)
 * - byte: status code (1 for success, 0 for failure)
 * - String: additional information or error message
 */
public class RegisterResponse implements Event {
    
    /** Message type identifier */
    private final int type = Protocol.REGISTER_RESPONSE;
    
    /** Status code: 1 for success, 0 for failure */
    private final byte statusCode;
    
    /** Additional information or error message */
    private final String additionalInfo;
    
    /**
     * Constructs a new RegisterResponse.
     * 
     * @param statusCode the status code (1 for success, 0 for failure)
     * @param additionalInfo additional information or error message
     */
    public RegisterResponse(byte statusCode, String additionalInfo) {
        this.statusCode = statusCode;
        this.additionalInfo = additionalInfo;
    }
    
    /**
     * Constructs a RegisterResponse by deserializing from bytes.
     * 
     * @param marshalledBytes the serialized message data
     * @throws IOException if deserialization fails or message type is invalid
     */
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
    
    /**
     * Gets the message type.
     * 
     * @return the protocol message type (REGISTER_RESPONSE)
     */
    @Override
    public int getType() {
        return type;
    }
    
    /**
     * Serializes this message to bytes for network transmission.
     * 
     * @return the serialized message as a byte array
     * @throws IOException if serialization fails
     */
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
    
    /**
     * Gets the status code.
     * 
     * @return 1 for success, 0 for failure
     */
    public byte getStatusCode() {
        return statusCode;
    }
    
    /**
     * Gets the additional information.
     * 
     * @return additional information or error message
     */
    public String getAdditionalInfo() {
        return additionalInfo;
    }
    
    /**
     * Checks if the registration was successful.
     * 
     * @return true if successful (status code = 1), false otherwise
     */
    public boolean isSuccess() {
        return statusCode == 1;
    }
}