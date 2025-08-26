package csx55.overlay.transport;

import csx55.overlay.util.LoggerUtil;
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
        try {
            this.dout = new DataOutputStream(socket.getOutputStream());
            this.din = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            LoggerUtil.error("TCPConnection", "Failed to create streams for connection", e);
            closeQuietly();
            throw e;
        }
        
        // Don't set remoteNodeId here - it will be set properly when we know the actual node ID
        this.remoteNodeId = null;
        
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
                // Connection closed - this is expected when socket is closed
                LoggerUtil.debug("TCPConnection", "Socket closed for " + remoteNodeId);
            } catch (IOException e) {
                LoggerUtil.error("TCPConnection", "Error reading from " + remoteNodeId, e);
            } finally {
                if (listener != null) {
                    listener.onConnectionLost(this);
                }
                closeQuietly();
            }
        });
        receiverThread.start();
    }
    
    public synchronized void sendEvent(Event event) throws IOException {
        if (!isConnected()) {
            throw new IOException("Connection is not active");
        }
        try {
            byte[] marshalledBytes = event.getBytes();
            dout.writeInt(marshalledBytes.length);
            dout.write(marshalledBytes);
            dout.flush();
        } catch (IOException e) {
            LoggerUtil.error("TCPConnection", "Failed to send event to " + remoteNodeId, e);
            closeQuietly();
            throw e;
        }
    }
    
    public String getRemoteNodeId() {
        return remoteNodeId;
    }
    
    public void setRemoteNodeId(String remoteNodeId) {
        this.remoteNodeId = remoteNodeId;
    }
    
    public synchronized void close() {
        closeQuietly();
    }
    
    private synchronized void closeQuietly() {
        try {
            if (din != null) {
                din.close();
            }
        } catch (IOException e) {
            LoggerUtil.debug("TCPConnection", "Error closing input stream: " + e.getMessage());
        }
        
        try {
            if (dout != null) {
                dout.close();
            }
        } catch (IOException e) {
            LoggerUtil.debug("TCPConnection", "Error closing output stream: " + e.getMessage());
        }
        
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            LoggerUtil.debug("TCPConnection", "Error closing socket: " + e.getMessage());
        }
    }
    
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
    
    public Socket getSocket() {
        return socket;
    }
}