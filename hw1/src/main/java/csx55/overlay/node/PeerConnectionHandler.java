package csx55.overlay.node;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class PeerConnectionHandler implements Runnable {
    private final Socket socket;
    private final MessagingNode node;
    private DataInputStream din;

    public PeerConnectionHandler(Socket socket, MessagingNode node) {
        this.socket = socket;
        this.node = node;
    }

    @Override
    public void run() {
        try {
            din = new DataInputStream(socket.getInputStream());
            // This loop will listen for incoming messages from the peer
            while (!socket.isClosed()) {
                // In Phase 2, you'll read message types and handle routing logic here
                int messageType = din.readInt();
                System.out.println("Received a message of type " + messageType + " from a peer.");
            }
        } catch (IOException e) {
            // Peer disconnected
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}