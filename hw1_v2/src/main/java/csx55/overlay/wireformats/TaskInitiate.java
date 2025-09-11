package csx55.overlay.wireformats;

import java.io.*;

public class TaskInitiate implements Event {
    private final int rounds;

    public TaskInitiate(int rounds) {
        this.rounds = rounds;
    }

    public TaskInitiate(DataInputStream in) throws IOException {
        this.rounds = in.readInt();
    }

    public int rounds() {
        return rounds;
    }

    @Override
    public int type() {
        return Protocol.TASK_INITIATE;
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeInt(type());
        out.writeInt(rounds);
    }
}