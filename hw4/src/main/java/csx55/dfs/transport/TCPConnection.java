package csx55.dfs.transport;

import java.io.*;
import java.net.Socket;

import csx55.dfs.protocol.Message;

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

    public void sendMessage(Message message) throws IOException {
        message.sendTo(outputStream);
    }

    public Message receiveMessage() throws IOException, ClassNotFoundException {
        return Message.receiveFrom(inputStream);
    }

    public void sendBytes(byte[] data) throws IOException {
        DataOutputStream dos = new DataOutputStream(outputStream);
        dos.writeInt(data.length);
        dos.write(data);
        dos.flush();
    }

    public byte[] receiveBytes() throws IOException {
        DataInputStream dis = new DataInputStream(inputStream);
        int length = dis.readInt();
        byte[] data = new byte[length];
        dis.readFully(data);
        return data;
    }

    public String getRemoteAddress() {
        return socket.getInetAddress().getHostName() + ":" + socket.getPort();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

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
