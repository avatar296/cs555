package csx55.overlay.wireformats;

import java.io.*;
import java.util.*;

/**
 * Registry → Node: list of peers this node should DIAL (initiate connections
 * to).
 *
 * Wire:
 * int type (Protocol.MESSAGING_NODE_LIST)
 * int count
 * count x String peerId // each is "ip:port"
 */
public class MessagingNodeList implements Event {
    private final List<String> peers;

    public MessagingNodeList(List<String> peers) {
        this.peers = List.copyOf(peers);
    }

    public MessagingNodeList(DataInputStream in) throws IOException {
        int n = in.readInt();
        List<String> ps = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            ps.add(in.readUTF());
        this.peers = Collections.unmodifiableList(ps);
    }

    public List<String> peers() {
        return peers;
    }

    @Override
    public int type() {
        return Protocol.MESSAGING_NODE_LIST;
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeInt(type());
        out.writeInt(peers.size());
        for (String p : peers)
            out.writeUTF(p);
    }
}