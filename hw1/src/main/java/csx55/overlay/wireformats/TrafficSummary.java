package csx55.overlay.wireformats;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Message containing traffic statistics from a messaging node.
 * Sent in response to a PullTrafficSummary request, includes counts
 * and sums for sent, received, and relayed messages.
 * 
 * Wire format:
 * - int: message type (TRAFFIC_SUMMARY)
 * - String: node IP address
 * - int: node port number
 * - int: messages sent count
 * - long: sum of sent message payloads
 * - int: messages received count
 * - long: sum of received message payloads
 * - int: messages relayed count
 */
public class TrafficSummary extends AbstractEvent {
    
    /** Message type identifier */
    private static final int TYPE = Protocol.TRAFFIC_SUMMARY;
    
    /** IP address of the reporting node */
    private String nodeIpAddress;
    
    /** Port number of the reporting node */
    private int nodePortNumber;
    
    /** Number of messages sent by this node */
    private int messagesSent;
    
    /** Sum of all sent message payloads */
    private long sumSentMessages;
    
    /** Number of messages received by this node */
    private int messagesReceived;
    
    /** Sum of all received message payloads */
    private long sumReceivedMessages;
    
    /** Number of messages relayed through this node */
    private int messagesRelayed;
    
    /**
     * Constructs a new TrafficSummary message.
     * 
     * @param nodeIpAddress the IP address of the reporting node
     * @param nodePortNumber the port number of the reporting node
     * @param messagesSent the number of messages sent
     * @param sumSentMessages the sum of sent message payloads
     * @param messagesReceived the number of messages received
     * @param sumReceivedMessages the sum of received message payloads
     * @param messagesRelayed the number of messages relayed
     */
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
    
    /**
     * Constructs a TrafficSummary by deserializing from bytes.
     * 
     * @param marshalledBytes the serialized message data
     * @throws IOException if deserialization fails or message type is invalid
     */
    public TrafficSummary(byte[] marshalledBytes) throws IOException {
        deserializeFrom(marshalledBytes);
    }
    
    /**
     * Gets the message type.
     * 
     * @return the protocol message type (TRAFFIC_SUMMARY)
     */
    @Override
    public int getType() {
        return TYPE;
    }
    
    /**
     * Writes the TrafficSummary-specific data to the output stream.
     * 
     * @param dout the data output stream
     * @throws IOException if writing fails
     */
    @Override
    protected void writeData(DataOutputStream dout) throws IOException {
        dout.writeUTF(nodeIpAddress);
        dout.writeInt(nodePortNumber);
        dout.writeInt(messagesSent);
        dout.writeLong(sumSentMessages);
        dout.writeInt(messagesReceived);
        dout.writeLong(sumReceivedMessages);
        dout.writeInt(messagesRelayed);
    }
    
    /**
     * Reads the TrafficSummary-specific data from the input stream.
     * 
     * @param din the data input stream
     * @throws IOException if reading fails
     */
    @Override
    protected void readData(DataInputStream din) throws IOException {
        this.nodeIpAddress = din.readUTF();
        this.nodePortNumber = din.readInt();
        this.messagesSent = din.readInt();
        this.sumSentMessages = din.readLong();
        this.messagesReceived = din.readInt();
        this.sumReceivedMessages = din.readLong();
        this.messagesRelayed = din.readInt();
    }
    
    /**
     * Gets the complete node identifier.
     * 
     * @return the node ID in "IP:port" format
     */
    public String getNodeId() {
        return nodeIpAddress + ":" + nodePortNumber;
    }
    
    /**
     * Gets the number of messages sent.
     * 
     * @return the count of sent messages
     */
    public int getMessagesSent() {
        return messagesSent;
    }
    
    /**
     * Gets the sum of sent message payloads.
     * 
     * @return the sum of all sent message payloads
     */
    public long getSumSentMessages() {
        return sumSentMessages;
    }
    
    /**
     * Gets the number of messages received.
     * 
     * @return the count of received messages
     */
    public int getMessagesReceived() {
        return messagesReceived;
    }
    
    /**
     * Gets the sum of received message payloads.
     * 
     * @return the sum of all received message payloads
     */
    public long getSumReceivedMessages() {
        return sumReceivedMessages;
    }
    
    /**
     * Gets the number of messages relayed.
     * 
     * @return the count of relayed messages
     */
    public int getMessagesRelayed() {
        return messagesRelayed;
    }
}