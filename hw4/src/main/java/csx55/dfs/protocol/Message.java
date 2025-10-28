/* CS555 Distributed Systems - HW4 */
package csx55.dfs.protocol;

import java.io.*;

/**
 * Base class for all protocol messages in the distributed file system
 *
 * <p>Messages are used for communication between: - Client and Controller - ChunkServer and
 * Controller - Client and ChunkServer - ChunkServer and ChunkServer
 */
public abstract class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Get the type of this message */
    public abstract MessageType getType();

    /** Serialize message to bytes for sending over network */
    public byte[] serialize() throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(this);
            return baos.toByteArray();
        }
    }

    /** Deserialize message from bytes received from network */
    public static Message deserialize(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (Message) ois.readObject();
        }
    }

    /** Send this message over a socket */
    public void sendTo(OutputStream out) throws IOException {
        byte[] data = serialize();
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(data.length); // Send length first
        dos.write(data); // Then send data
        dos.flush();
    }

    /** Receive a message from a socket */
    public static Message receiveFrom(InputStream in) throws IOException, ClassNotFoundException {
        DataInputStream dis = new DataInputStream(in);
        int length = dis.readInt(); // Read length first
        byte[] data = new byte[length];
        dis.readFully(data); // Then read data
        return deserialize(data);
    }
}
