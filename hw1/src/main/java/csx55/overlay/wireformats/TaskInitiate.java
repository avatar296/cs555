package csx55.overlay.wireformats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
public class TaskInitiate implements Event {
    
    /** Message type identifier */
    private final int type = Protocol.TASK_INITIATE;
    
    /** Number of messaging rounds to execute */
    private final int rounds;
    
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
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
        DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));
        
        int messageType = din.readInt();
        if (messageType != Protocol.TASK_INITIATE) {
            throw new IOException("Invalid message type for TaskInitiate");
        }
        
        this.rounds = din.readInt();
        
        baInputStream.close();
        din.close();
    }
    
    /**
     * Gets the message type.
     * 
     * @return the protocol message type (TASK_INITIATE)
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
        dout.writeInt(rounds);
        dout.flush();
        
        byte[] marshalledBytes = baOutputStream.toByteArray();
        baOutputStream.close();
        dout.close();
        
        return marshalledBytes;
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