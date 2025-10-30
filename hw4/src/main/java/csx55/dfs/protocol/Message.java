/* CS555 Distributed Systems - HW4 */
package csx55.dfs.protocol;

import java.io.*;

public abstract class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    public abstract MessageType getType();

    public byte[] serialize() throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(this);
            return baos.toByteArray();
        }
    }

    public static Message deserialize(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (Message) ois.readObject();
        }
    }

    public void sendTo(OutputStream out) throws IOException {
        byte[] data = serialize();
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(data.length);
        dos.write(data);
        dos.flush();
    }

    public static Message receiveFrom(InputStream in) throws IOException, ClassNotFoundException {
        DataInputStream dis = new DataInputStream(in);
        int length = dis.readInt();
        byte[] data = new byte[length];
        dis.readFully(data);
        return deserialize(data);
    }
}
