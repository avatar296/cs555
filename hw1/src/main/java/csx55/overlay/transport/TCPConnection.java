package csx55.overlay.transport;

import csx55.overlay.wireformats.Event;
import csx55.overlay.wireformats.EventFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

public class TCPConnection {
    
    private Socket socket;
    private DataOutputStream dout;
    private DataInputStream din;
    private String remoteNodeId;
    private Thread receiverThread;
    private TCPConnectionListener listener;
    
    public interface TCPConnectionListener {
        void onEvent(Event event, TCPConnection connection);
        void onConnectionLost(TCPConnection connection);
    }
    
    public TCPConnection(Socket socket, TCPConnectionListener listener) throws IOException {
        this.socket = socket;
        this.listener = listener;
        this.dout = new DataOutputStream(socket.getOutputStream());
        this.din = new DataInputStream(socket.getInputStream());
        
        String ip = socket.getInetAddress().getHostAddress();
        int port = socket.getPort();
        this.remoteNodeId = ip + ":" + port;
        
        startReceiver();
    }
    
    public TCPConnection(String host, int port, TCPConnectionListener listener) throws IOException {
        this(new Socket(host, port), listener);
    }
    
    private void startReceiver() {
        receiverThread = new Thread(() -> {
            EventFactory factory = EventFactory.getInstance();
            
            try {
                while (!socket.isClosed()) {
                    int dataLength = din.readInt();
                    byte[] data = new byte[dataLength];
                    din.readFully(data, 0, dataLength);
                    
                    Event event = factory.createEvent(data);
                    
                    if (listener != null) {
                        listener.onEvent(event, this);
                    }
                }
            } catch (SocketException e) {
                // Connection closed
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (listener != null) {
                    listener.onConnectionLost(this);
                }
                close();
            }
        });
        receiverThread.start();
    }
    
    public synchronized void sendEvent(Event event) throws IOException {
        byte[] marshalledBytes = event.getBytes();
        dout.writeInt(marshalledBytes.length);
        dout.write(marshalledBytes);
        dout.flush();
    }
    
    public String getRemoteNodeId() {
        return remoteNodeId;
    }
    
    public void setRemoteNodeId(String remoteNodeId) {
        this.remoteNodeId = remoteNodeId;
    }
    
    public synchronized void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }
    
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
    
    public Socket getSocket() {
        return socket;
    }
}