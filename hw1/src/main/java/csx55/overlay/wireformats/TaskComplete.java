package csx55.overlay.wireformats;

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
public class TaskComplete extends AbstractEvent {
    
    /** Message type identifier */
    private static final int TYPE = Protocol.TASK_COMPLETE;
    
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
        deserializeFrom(marshalledBytes);
    }
    
    /**
     * Gets the message type.
     * 
     * @return the protocol message type (TASK_COMPLETE)
     */
    @Override
    public int getType() {
        return TYPE;
    }
    
    /**
     * Writes the TaskComplete-specific data to the output stream.
     * 
     * @param dout the data output stream
     * @throws IOException if writing fails
     */
    @Override
    protected void writeData(DataOutputStream dout) throws IOException {
        dout.writeUTF(nodeIpAddress);
        dout.writeInt(nodePortNumber);
    }
    
    /**
     * Reads the TaskComplete-specific data from the input stream.
     * 
     * @param din the data input stream
     * @throws IOException if reading fails
     */
    @Override
    protected void readData(DataInputStream din) throws IOException {
        this.nodeIpAddress = din.readUTF();
        this.nodePortNumber = din.readInt();
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