package csx55.overlay.wireformats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a data message routed through the overlay network.
 * Contains a payload that is transmitted from a source node to a
 * destination (sink) node, potentially through intermediate relay nodes.
 * 
 * Wire format:
 * - int: message type (DATA_MESSAGE)
 * - String: source node ID
 * - String: sink node ID  
 * - int: payload value
 */
public class DataMessage implements Event {
    
    /** Message type identifier */
    private final int type = Protocol.DATA_MESSAGE;
    
    /** Identifier of the node that originated this message */
    private final String sourceNodeId;
    
    /** Identifier of the destination node */
    private final String sinkNodeId;
    
    /** The data payload being transmitted */
    private final int payload;
    
    /**
     * Constructs a new DataMessage.
     * 
     * @param sourceNodeId the identifier of the source node
     * @param sinkNodeId the identifier of the destination node
     * @param payload the data payload to transmit
     */
    public DataMessage(String sourceNodeId, String sinkNodeId, int payload) {
        this.sourceNodeId = sourceNodeId;
        this.sinkNodeId = sinkNodeId;
        this.payload = payload;
    }
    
    /**
     * Constructs a DataMessage by deserializing from bytes.
     * 
     * @param marshalledBytes the serialized message data
     * @throws IOException if deserialization fails or message type is invalid
     */
    public DataMessage(byte[] marshalledBytes) throws IOException {
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
        DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));
        
        int messageType = din.readInt();
        if (messageType != Protocol.DATA_MESSAGE) {
            throw new IOException("Invalid message type for DataMessage");
        }
        
        this.sourceNodeId = din.readUTF();
        this.sinkNodeId = din.readUTF();
        this.payload = din.readInt();
        
        baInputStream.close();
        din.close();
    }
    
    /**
     * Gets the message type.
     * 
     * @return the protocol message type (DATA_MESSAGE)
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
        dout.writeUTF(sourceNodeId);
        dout.writeUTF(sinkNodeId);
        dout.writeInt(payload);
        dout.flush();
        
        byte[] marshalledBytes = baOutputStream.toByteArray();
        baOutputStream.close();
        dout.close();
        
        return marshalledBytes;
    }
    
    /**
     * Gets the source node identifier.
     * 
     * @return the ID of the node that originated this message
     */
    public String getSourceNodeId() {
        return sourceNodeId;
    }
    
    /**
     * Gets the destination node identifier.
     * 
     * @return the ID of the destination node
     */
    public String getSinkNodeId() {
        return sinkNodeId;
    }
    
    /**
     * Gets the message payload.
     * 
     * @return the data payload
     */
    public int getPayload() {
        return payload;
    }
}