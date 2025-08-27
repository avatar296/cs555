package csx55.overlay.wireformats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Message containing a list of peer nodes that a messaging node should connect to.
 * Sent by the registry after overlay setup to instruct each node which peers
 * to establish connections with based on the overlay topology.
 * 
 * Wire format:
 * - int: message type (MESSAGING_NODES_LIST)
 * - int: number of peer nodes
 * - For each peer: String containing node identifier (IP:port)
 */
public class MessagingNodesList implements Event {
    
    /** Message type identifier */
    private final int type = Protocol.MESSAGING_NODES_LIST;
    
    /** Number of peer nodes to connect to */
    private final int numberOfPeerNodes;
    
    /** List of peer node identifiers (IP:port format) */
    private final List<String> peerNodes;
    
    /**
     * Constructs a new MessagingNodesList.
     * 
     * @param peerNodes the list of peer node identifiers
     */
    public MessagingNodesList(List<String> peerNodes) {
        this.peerNodes = peerNodes;
        this.numberOfPeerNodes = peerNodes.size();
    }
    
    /**
     * Constructs a MessagingNodesList by deserializing from bytes.
     * 
     * @param marshalledBytes the serialized message data
     * @throws IOException if deserialization fails or message type is invalid
     */
    public MessagingNodesList(byte[] marshalledBytes) throws IOException {
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
        DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));
        
        int messageType = din.readInt();
        if (messageType != Protocol.MESSAGING_NODES_LIST) {
            throw new IOException("Invalid message type for MessagingNodesList");
        }
        
        this.numberOfPeerNodes = din.readInt();
        this.peerNodes = new ArrayList<>();
        
        for (int i = 0; i < numberOfPeerNodes; i++) {
            String nodeInfo = din.readUTF();
            peerNodes.add(nodeInfo);
        }
        
        baInputStream.close();
        din.close();
    }
    
    /**
     * Gets the message type.
     * 
     * @return the protocol message type (MESSAGING_NODES_LIST)
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
        dout.writeInt(numberOfPeerNodes);
        
        for (String nodeInfo : peerNodes) {
            dout.writeUTF(nodeInfo);
        }
        
        dout.flush();
        
        byte[] marshalledBytes = baOutputStream.toByteArray();
        baOutputStream.close();
        dout.close();
        
        return marshalledBytes;
    }
    
    /**
     * Gets the number of peer nodes.
     * 
     * @return the count of peer nodes in the list
     */
    public int getNumberOfPeerNodes() {
        return numberOfPeerNodes;
    }
    
    /**
     * Gets the list of peer node identifiers.
     * Each identifier is in the format "IP:port".
     * 
     * @return the list of peer node identifiers
     */
    public List<String> getPeerNodes() {
        return peerNodes;
    }
}