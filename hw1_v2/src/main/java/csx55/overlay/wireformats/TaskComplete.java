package csx55.overlay.wireformats;

import java.io.*;

public class TaskComplete implements Event {
    private final String ip;
    private final int port;

    public TaskComplete(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public TaskComplete(DataInputStream in) throws IOException {
        this.ip = in.readUTF();
        this.port = in.readInt();
    }

    public String ip() {
        return ip;
    }

    public int port() {
        return port;
    }

    @Override
    public int type() {
        return Protocol.TASK_COMPLETE;
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeInt(type());
        out.writeUTF(ip);
        out.writeInt(port);
    }
}