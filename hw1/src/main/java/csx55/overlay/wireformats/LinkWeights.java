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
 * Message containing the weighted links of the overlay network topology.
 * Sent by the registry to all nodes after overlay setup to enable
 * shortest path routing calculations.
 * 
 * Wire format:
 * - int: message type (LINK_WEIGHTS)
 * - int: number of links
 * - For each link: String containing "nodeA nodeB weight"
 */
public class LinkWeights implements Event {
    
    /** Message type identifier */
    private final int type = Protocol.LINK_WEIGHTS;
    
    /** Number of links in the overlay */
    private final int numberOfLinks;
    
    /** List of all weighted links in the topology */
    private final List<LinkInfo> links;
    
    /**
     * Represents a weighted link between two nodes in the overlay.
     */
    public static class LinkInfo {
        /** First node in the link */
        public final String nodeA;
        
        /** Second node in the link */
        public final String nodeB;
        
        /** Weight/cost of this link */
        public final int weight;
        
        /**
         * Constructs a new LinkInfo.
         * 
         * @param nodeA first node identifier
         * @param nodeB second node identifier
         * @param weight the link weight
         */
        public LinkInfo(String nodeA, String nodeB, int weight) {
            this.nodeA = nodeA;
            this.nodeB = nodeB;
            this.weight = weight;
        }
        
        /**
         * Returns a string representation of this link.
         * 
         * @return formatted string as "nodeA, nodeB, weight"
         */
        @Override
        public String toString() {
            return nodeA + ", " + nodeB + ", " + weight;
        }
    }
    
    /**
     * Constructs a new LinkWeights message.
     * 
     * @param links the list of weighted links in the topology
     */
    public LinkWeights(List<LinkInfo> links) {
        this.links = links;
        this.numberOfLinks = links.size();
    }
    
    /**
     * Constructs a LinkWeights message by deserializing from bytes.
     * 
     * @param marshalledBytes the serialized message data
     * @throws IOException if deserialization fails or message type is invalid
     */
    public LinkWeights(byte[] marshalledBytes) throws IOException {
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
        DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));
        
        int messageType = din.readInt();
        if (messageType != Protocol.LINK_WEIGHTS) {
            throw new IOException("Invalid message type for LinkWeights");
        }
        
        this.numberOfLinks = din.readInt();
        this.links = new ArrayList<>();
        
        for (int i = 0; i < numberOfLinks; i++) {
            String linkData = din.readUTF();
            String[] parts = linkData.split(" ");
            if (parts.length == 3) {
                links.add(new LinkInfo(parts[0], parts[1], Integer.parseInt(parts[2])));
            }
        }
        
        baInputStream.close();
        din.close();
    }
    
    /**
     * Gets the message type.
     * 
     * @return the protocol message type (LINK_WEIGHTS)
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
        dout.writeInt(numberOfLinks);
        
        for (LinkInfo link : links) {
            dout.writeUTF(link.nodeA + " " + link.nodeB + " " + link.weight);
        }
        
        dout.flush();
        
        byte[] marshalledBytes = baOutputStream.toByteArray();
        baOutputStream.close();
        dout.close();
        
        return marshalledBytes;
    }
    
    /**
     * Gets the number of links in the topology.
     * 
     * @return the number of links
     */
    public int getNumberOfLinks() {
        return numberOfLinks;
    }
    
    /**
     * Gets the list of weighted links.
     * 
     * @return the list of LinkInfo objects
     */
    public List<LinkInfo> getLinks() {
        return links;
    }
}