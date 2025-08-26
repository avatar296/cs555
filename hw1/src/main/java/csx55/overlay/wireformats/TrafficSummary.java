package csx55.overlay.wireformats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class TrafficSummary implements Event {
    
    private final int type = Protocol.TRAFFIC_SUMMARY;
    private String nodeIpAddress;
    private int nodePortNumber;
    private int messagesSent;
    private long sumSentMessages;
    private int messagesReceived;
    private long sumReceivedMessages;
    private int messagesRelayed;
    
    public TrafficSummary(String nodeIpAddress, int nodePortNumber, 
                          int messagesSent, long sumSentMessages,
                          int messagesReceived, long sumReceivedMessages,
                          int messagesRelayed) {
        this.nodeIpAddress = nodeIpAddress;
        this.nodePortNumber = nodePortNumber;
        this.messagesSent = messagesSent;
        this.sumSentMessages = sumSentMessages;
        this.messagesReceived = messagesReceived;
        this.sumReceivedMessages = sumReceivedMessages;
        this.messagesRelayed = messagesRelayed;
    }
    
    public TrafficSummary(byte[] marshalledBytes) throws IOException {
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
        DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));
        
        int messageType = din.readInt();
        if (messageType != Protocol.TRAFFIC_SUMMARY) {
            throw new IOException("Invalid message type for TrafficSummary");
        }
        
        this.nodeIpAddress = din.readUTF();
        this.nodePortNumber = din.readInt();
        this.messagesSent = din.readInt();
        this.sumSentMessages = din.readLong();
        this.messagesReceived = din.readInt();
        this.sumReceivedMessages = din.readLong();
        this.messagesRelayed = din.readInt();
        
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
        dout.writeUTF(nodeIpAddress);
        dout.writeInt(nodePortNumber);
        dout.writeInt(messagesSent);
        dout.writeLong(sumSentMessages);
        dout.writeInt(messagesReceived);
        dout.writeLong(sumReceivedMessages);
        dout.writeInt(messagesRelayed);
        dout.flush();
        
        byte[] marshalledBytes = baOutputStream.toByteArray();
        baOutputStream.close();
        dout.close();
        
        return marshalledBytes;
    }
    
    public String getNodeId() {
        return nodeIpAddress + ":" + nodePortNumber;
    }
    
    public int getMessagesSent() {
        return messagesSent;
    }
    
    public long getSumSentMessages() {
        return sumSentMessages;
    }
    
    public int getMessagesReceived() {
        return messagesReceived;
    }
    
    public long getSumReceivedMessages() {
        return sumReceivedMessages;
    }
    
    public int getMessagesRelayed() {
        return messagesRelayed;
    }
}