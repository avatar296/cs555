package csx55.overlay.wireformats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Notification message sent by a node to indicate task completion.
 * After a node finishes sending all required messages in a messaging task,
 * it sends this message to the registry to report completion.
 * 
 * Wire format:
 * - int: message type (TASK_COMPLETE)
 * - String: IP address of the node
 * - int: port number of the node
 */
public class TaskComplete implements Event {
    
    /** Message type identifier */
    private final int type = Protocol.TASK_COMPLETE;
    
    /** IP address of the node that completed the task */
    private String nodeIpAddress;
    
    /** Port number of the node that completed the task */
    private int nodePortNumber;
    
    /**
     * Constructs a new TaskComplete message.
     * 
     * @param nodeIpAddress the IP address of the completing node
     * @param nodePortNumber the port number of the completing node
     */
    public TaskComplete(String nodeIpAddress, int nodePortNumber) {
        this.nodeIpAddress = nodeIpAddress;
        this.nodePortNumber = nodePortNumber;
    }
    
    /**
     * Constructs a TaskComplete by deserializing from bytes.
     * 
     * @param marshalledBytes the serialized message data
     * @throws IOException if deserialization fails or message type is invalid
     */
    public TaskComplete(byte[] marshalledBytes) throws IOException {
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(marshalledBytes);
        DataInputStream din = new DataInputStream(new BufferedInputStream(baInputStream));
        
        int messageType = din.readInt();
        if (messageType != Protocol.TASK_COMPLETE) {
            throw new IOException("Invalid message type for TaskComplete");
        }
        
        this.nodeIpAddress = din.readUTF();
        this.nodePortNumber = din.readInt();
        
        baInputStream.close();
        din.close();
    }
    
    /**
     * Gets the message type.
     * 
     * @return the protocol message type (TASK_COMPLETE)
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
        dout.writeUTF(nodeIpAddress);
        dout.writeInt(nodePortNumber);
        dout.flush();
        
        byte[] marshalledBytes = baOutputStream.toByteArray();
        baOutputStream.close();
        dout.close();
        
        return marshalledBytes;
    }
    
    /**
     * Gets the IP address of the node that completed the task.
     * 
     * @return the node's IP address
     */
    public String getNodeIpAddress() {
        return nodeIpAddress;
    }
    
    /**
     * Gets the port number of the node that completed the task.
     * 
     * @return the node's port number
     */
    public int getNodePortNumber() {
        return nodePortNumber;
    }
    
    /**
     * Gets the complete node identifier.
     * 
     * @return the node ID in "IP:port" format
     */
    public String getNodeId() {
        return nodeIpAddress + ":" + nodePortNumber;
    }
}