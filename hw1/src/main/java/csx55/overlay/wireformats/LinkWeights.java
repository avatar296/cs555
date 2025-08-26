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

public class LinkWeights implements Event {
    
    private final int type = Protocol.LINK_WEIGHTS;
    private int numberOfLinks;
    private List<LinkInfo> links;
    
    public static class LinkInfo {
        public String nodeA;
        public String nodeB;
        public int weight;
        
        public LinkInfo(String nodeA, String nodeB, int weight) {
            this.nodeA = nodeA;
            this.nodeB = nodeB;
            this.weight = weight;
        }
        
        @Override
        public String toString() {
            return nodeA + ", " + nodeB + ", " + weight;
        }
    }
    
    public LinkWeights(List<LinkInfo> links) {
        this.links = links;
        this.numberOfLinks = links.size();
    }
    
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
    
    @Override
    public int getType() {
        return type;
    }
    
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
    
    public int getNumberOfLinks() {
        return numberOfLinks;
    }
    
    public List<LinkInfo> getLinks() {
        return links;
    }
}