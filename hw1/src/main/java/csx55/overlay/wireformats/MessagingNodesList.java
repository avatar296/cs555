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

public class MessagingNodesList implements Event {
    
    private final int type = Protocol.MESSAGING_NODES_LIST;
    private int numberOfPeerNodes;
    private List<String> peerNodes;
    
    public MessagingNodesList(List<String> peerNodes) {
        this.peerNodes = peerNodes;
        this.numberOfPeerNodes = peerNodes.size();
    }
    
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
    
    @Override
    public int getType() {
        return type;
    }
    
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
    
    public int getNumberOfPeerNodes() {
        return numberOfPeerNodes;
    }
    
    public List<String> getPeerNodes() {
        return peerNodes;
    }
}