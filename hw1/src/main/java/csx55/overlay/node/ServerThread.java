package csx55.overlay.node;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;

public class ServerThread implements Runnable {
    private final Socket clientSocket;
    private final Registry registry;
    private DataOutputStream dout;
    private String nodeId;

    public ServerThread(Socket clientSocket, Registry registry) {
        this.clientSocket = clientSocket;
        this.registry = registry;
    }

    @Override
    public void run() {
        try {
            dout = new DataOutputStream(clientSocket.getOutputStream());
            DataInputStream din = new DataInputStream(clientSocket.getInputStream());

            int messageType = din.readInt();
            if (messageType == 1) { // REGISTER_REQUEST
                String nodeIP = din.readUTF();
                int nodePort = din.readInt();
                this.nodeId = nodeIP + ":" + nodePort;

                registry.registerNode(nodeId, this);

                dout.writeInt(2); // REGISTER_RESPONSE
                dout.writeByte(1); // SUCCESS
                String info = "Registration successful. Total nodes: " + (registry.getRegisteredNodeCount());
                dout.writeUTF(info);
                dout.flush();
            }

            while (!clientSocket.isClosed()) {
                Thread.sleep(1000);
            }
        } catch (IOException | InterruptedException e) {
            // Node disconnected
        } finally {
            if (nodeId != null) {
                registry.deregisterNode(nodeId);
            }
        }
    }

    public void sendPeerList(List<String> peers) throws IOException {
        dout.writeInt(3); // MESSAGING_NODES_LIST
        dout.writeInt(peers.size());
        for (String peer : peers) {
            dout.writeUTF(peer);
        }
        dout.flush();
    }

    public void sendLinkWeights(List<String> weightedLinks) throws IOException {
        dout.writeInt(4); // LINK_WEIGHTS
        dout.writeInt(weightedLinks.size());
        for (String link : weightedLinks) {
            dout.writeUTF(link);
        }
        dout.flush();
    }
}