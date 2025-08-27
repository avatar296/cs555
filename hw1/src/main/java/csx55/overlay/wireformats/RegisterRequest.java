package csx55.overlay.wireformats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Request message sent by a node to register with the overlay network.
 * Contains the node's IP address and port number for the registry to
 * validate and add the node to the overlay topology.
 * 
 * Wire format:
 * - int: message type (REGISTER_REQUEST)
 * - String: IP address of the node
 * - int: port number of the node
 */
public class RegisterRequest implements Event {
    
    /** Message type identifier */
    private final int type = Protocol.REGISTER_REQUEST;
    
    /** IP address of the node requesting registration */
    private String ipAddress;
    
    /** Port number of the node requesting registration */
    private int portNumber;
    
    /**
     * Constructs a new RegisterRequest.
     * 
     * @param ipAddress the IP address of the registering node
     * @param portNumber the port number of the registering node
     */
    public RegisterRequest(String ipAddress, int portNumber) {
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;
    }
    
    /**
     * Constructs a RegisterRequest by deserializing from bytes.
     * 
     * @param marshalledBytes the serialized message data
     * @throws IOException if deserialization fails or message type is invalid
     */
    public RegisterRequest(byte[] marshalledBytes) throws IOException {
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
        DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));
        
        int messageType = din.readInt();
        if (messageType != Protocol.REGISTER_REQUEST) {
            throw new IOException("Invalid message type for RegisterRequest");
        }
        
        this.ipAddress = din.readUTF();
        this.portNumber = din.readInt();
        
        baInputStream.close();
        din.close();
    }
    
    /**
     * Gets the message type.
     * 
     * @return the protocol message type (REGISTER_REQUEST)
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
        dout.writeUTF(ipAddress);
        dout.writeInt(portNumber);
        dout.flush();
        
        byte[] marshalledBytes = baOutputStream.toByteArray();
        baOutputStream.close();
        dout.close();
        
        return marshalledBytes;
    }
    
    /**
     * Gets the IP address of the registering node.
     * 
     * @return the node's IP address
     */
    public String getIpAddress() {
        return ipAddress;
    }
    
    /**
     * Gets the port number of the registering node.
     * 
     * @return the node's port number
     */
    public int getPortNumber() {
        return portNumber;
    }
}