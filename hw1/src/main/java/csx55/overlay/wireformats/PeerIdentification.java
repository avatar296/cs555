package csx55.overlay.wireformats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Message sent between peers to identify themselves after connection establishment.
 * When two messaging nodes establish a TCP connection, they exchange identification
 * messages to properly map connections to node identifiers.
 * 
 * Wire format:
 * - int: message type (PEER_IDENTIFICATION)
 * - String: node identifier (IP:port format)
 */
public class PeerIdentification implements Event {
    
    /** Message type identifier */
    private final int type = Protocol.PEER_IDENTIFICATION;
    
    /** Identifier of the sending node (IP:port format) */
    private final String nodeId;
    
    /**
     * Constructs a new PeerIdentification message.
     * 
     * @param nodeId the identifier of the sending node
     */
    public PeerIdentification(String nodeId) {
        this.nodeId = nodeId;
    }
    
    /**
     * Constructs a PeerIdentification by deserializing from bytes.
     * 
     * @param marshalledBytes the serialized message data
     * @throws IOException if deserialization fails or message type is invalid
     */
    public PeerIdentification(byte[] marshalledBytes) throws IOException {
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
        DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));
        
        int messageType = din.readInt();
        if (messageType != Protocol.PEER_IDENTIFICATION) {
            throw new IOException("Invalid message type for PeerIdentification");
        }
        
        this.nodeId = din.readUTF();
        
        baInputStream.close();
        din.close();
    }
    
    /**
     * Gets the message type.
     * 
     * @return the protocol message type (PEER_IDENTIFICATION)
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
        dout.writeUTF(nodeId);
        dout.flush();
        
        byte[] marshalledBytes = baOutputStream.toByteArray();
        baOutputStream.close();
        dout.close();
        
        return marshalledBytes;
    }
    
    /**
     * Gets the node identifier of the sender.
     * 
     * @return the node identifier in IP:port format
     */
    public String getNodeId() {
        return nodeId;
    }
}