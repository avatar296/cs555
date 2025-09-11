package csx55.overlay.wireformats;

import java.io.DataOutputStream;
import java.io.IOException;

public interface Event {
    int type();

    void write(DataOutputStream out) throws IOException;
}