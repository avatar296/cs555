package csx55.overlay.wireformats;

import java.io.IOException;

public interface Event {
    
    int getType();
    
    byte[] getBytes() throws IOException;
    
}