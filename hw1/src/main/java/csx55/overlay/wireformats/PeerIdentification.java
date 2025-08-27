package csx55.overlay.wireformats;

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
public class PeerIdentification extends AbstractEvent {
    
    /** Message type identifier */
    private static final int TYPE = Protocol.PEER_IDENTIFICATION;
    
    /** Identifier of the sending node (IP:port format) */
    private String nodeId;
    
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
        deserializeFrom(marshalledBytes);
    }
    
    /**
     * Gets the message type.
     * 
     * @return the protocol message type (PEER_IDENTIFICATION)
     */
    @Override
    public int getType() {
        return TYPE;
    }
    
    /**
     * Writes the PeerIdentification-specific data to the output stream.
     * 
     * @param dout the data output stream
     * @throws IOException if writing fails
     */
    @Override
    protected void writeData(DataOutputStream dout) throws IOException {
        dout.writeUTF(nodeId);
    }
    
    /**
     * Reads the PeerIdentification-specific data from the input stream.
     * 
     * @param din the data input stream
     * @throws IOException if reading fails
     */
    @Override
    protected void readData(DataInputStream din) throws IOException {
        this.nodeId = din.readUTF();
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