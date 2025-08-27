package csx55.overlay.wireformats;

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
public class PullTrafficSummary extends AbstractEvent {
    
    /** Message type identifier */
    private static final int TYPE = Protocol.PULL_TRAFFIC_SUMMARY;
    
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
        deserializeFrom(marshalledBytes);
    }
    
    /**
     * Gets the message type.
     * 
     * @return the protocol message type (PULL_TRAFFIC_SUMMARY)
     */
    @Override
    public int getType() {
        return TYPE;
    }
    
    /**
     * Writes the PullTrafficSummary-specific data to the output stream.
     * This message has no additional data beyond the type.
     * 
     * @param dout the data output stream
     * @throws IOException if writing fails
     */
    @Override
    protected void writeData(DataOutputStream dout) throws IOException {
        // No additional data to write
    }
    
    /**
     * Reads the PullTrafficSummary-specific data from the input stream.
     * This message has no additional data beyond the type.
     * 
     * @param din the data input stream
     * @throws IOException if reading fails
     */
    @Override
    protected void readData(DataInputStream din) throws IOException {
        // No additional data to read
    }
}