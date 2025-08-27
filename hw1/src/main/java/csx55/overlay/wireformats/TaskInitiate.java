package csx55.overlay.wireformats;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Command message sent by the registry to initiate messaging tasks.
 * Instructs nodes to begin sending data messages for the specified
 * number of rounds to test the overlay network.
 * 
 * Wire format:
 * - int: message type (TASK_INITIATE)
 * - int: number of rounds to execute
 */
public class TaskInitiate extends AbstractEvent {
    
    /** Message type identifier */
    private static final int TYPE = Protocol.TASK_INITIATE;
    
    /** Number of messaging rounds to execute */
    private int rounds;
    
    /**
     * Constructs a new TaskInitiate message.
     * 
     * @param rounds the number of messaging rounds to execute
     */
    public TaskInitiate(int rounds) {
        this.rounds = rounds;
    }
    
    /**
     * Constructs a TaskInitiate by deserializing from bytes.
     * 
     * @param marshalledBytes the serialized message data
     * @throws IOException if deserialization fails or message type is invalid
     */
    public TaskInitiate(byte[] marshalledBytes) throws IOException {
        deserializeFrom(marshalledBytes);
    }
    
    /**
     * Gets the message type.
     * 
     * @return the protocol message type (TASK_INITIATE)
     */
    @Override
    public int getType() {
        return TYPE;
    }
    
    /**
     * Writes the TaskInitiate-specific data to the output stream.
     * 
     * @param dout the data output stream
     * @throws IOException if writing fails
     */
    @Override
    protected void writeData(DataOutputStream dout) throws IOException {
        dout.writeInt(rounds);
    }
    
    /**
     * Reads the TaskInitiate-specific data from the input stream.
     * 
     * @param din the data input stream
     * @throws IOException if reading fails
     */
    @Override
    protected void readData(DataInputStream din) throws IOException {
        this.rounds = din.readInt();
    }
    
    /**
     * Gets the number of rounds to execute.
     * 
     * @return the number of messaging rounds
     */
    public int getRounds() {
        return rounds;
    }
}