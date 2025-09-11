package csx55.overlay.wireformats;

import java.io.*;

public class PeerHello implements Event {
    private final String nodeId;

    public PeerHello(String nodeId) {
        this.nodeId = nodeId;
    }

    public PeerHello(DataInputStream in) throws IOException {
        this.nodeId = in.readUTF();
    }

    public String nodeId() {
        return nodeId;
    }

    @Override
    public int type() {
        return Protocol.PEER_HELLO;
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeInt(type());
        out.writeUTF(nodeId);
    }
}
