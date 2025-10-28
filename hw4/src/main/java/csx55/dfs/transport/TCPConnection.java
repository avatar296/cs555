package csx55.dfs.transport;

import csx55.dfs.protocol.Message;

import java.io.*;
import java.net.Socket;

/**
 * Wrapper for TCP socket connections
 * Handles message sending/receiving over TCP
 */
public class TCPConnection implements AutoCloseable {

    private final Socket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    public TCPConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
    }

    public TCPConnection(String host, int port) throws IOException {
        this(new Socket(host, port));
    }

    /**
     * Send a message over this connection
     */
    public void sendMessage(Message message) throws IOException {
        message.sendTo(outputStream);
    }

    /**
     * Receive a message from this connection
     */
    public Message receiveMessage() throws IOException, ClassNotFoundException {
        return Message.receiveFrom(inputStream);
    }

    /**
     * Send raw bytes
     */
    public void sendBytes(byte[] data) throws IOException {
        DataOutputStream dos = new DataOutputStream(outputStream);
        dos.writeInt(data.length);
        dos.write(data);
        dos.flush();
    }

    /**
     * Receive raw bytes
     */
    public byte[] receiveBytes() throws IOException {
        DataInputStream dis = new DataInputStream(inputStream);
        int length = dis.readInt();
        byte[] data = new byte[length];
        dis.readFully(data);
        return data;
    }

    /**
     * Get the remote address as "ip:port"
     */
    public String getRemoteAddress() {
        return socket.getInetAddress().getHostName() + ":" + socket.getPort();
    }

    /**
     * Check if connection is still open
     */
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    /**
     * Parse "ip:port" string into host and port
     */
    public static class Address {
        public final String host;
        public final int port;

        public Address(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public static Address parse(String address) {
            String[] parts = address.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid address format: " + address);
            }
            return new Address(parts[0], Integer.parseInt(parts[1]));
        }
    }
}
