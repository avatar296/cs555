package csx55.overlay.wireformats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Request message sent by the registry to pull traffic statistics from nodes.
 * This message instructs nodes to report their message traffic summaries
 * including sent/received counts and payload sums.
 * 
 * Wire format:
 * - int: message type (PULL_TRAFFIC_SUMMARY)
 */
public class PullTrafficSummary implements Event {
    
    /** Message type identifier */
    private final int type = Protocol.PULL_TRAFFIC_SUMMARY;
    
    /**
     * Constructs a new PullTrafficSummary request.
     */
    public PullTrafficSummary() {
    }
    
    /**
     * Constructs a PullTrafficSummary by deserializing from bytes.
     * 
     * @param marshalledBytes the serialized message data
     * @throws IOException if deserialization fails or message type is invalid
     */
    public PullTrafficSummary(byte[] marshalledBytes) throws IOException {
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
        DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));
        
        int messageType = din.readInt();
        if (messageType != Protocol.PULL_TRAFFIC_SUMMARY) {
            throw new IOException("Invalid message type for PullTrafficSummary");
        }
        
        baInputStream.close();
        din.close();
    }
    
    /**
     * Gets the message type.
     * 
     * @return the protocol message type (PULL_TRAFFIC_SUMMARY)
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
        dout.flush();
        
        byte[] marshalledBytes = baOutputStream.toByteArray();
        baOutputStream.close();
        dout.close();
        
        return marshalledBytes;
    }
}