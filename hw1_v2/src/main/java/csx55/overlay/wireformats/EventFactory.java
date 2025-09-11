package csx55.overlay.wireformats;

import java.io.DataInputStream;
import java.io.IOException;

public final class EventFactory {
    private static final EventFactory INSTANCE = new EventFactory();

    private EventFactory() {
    }

    public static EventFactory getInstance() {
        return INSTANCE;
    }

    public Event read(DataInputStream in) throws IOException {
        int t = in.readInt();
        switch (t) {
            case Protocol.REGISTER:
                return new Register(in);
            case Protocol.REGISTER_RESPONSE:
                return new RegisterResponse(in);
            case Protocol.DEREGISTER:
                return new Deregister(in);
            case Protocol.DEREGISTER_RESPONSE:
                return new DeregisterResponse(in);
            default:
                throw new IOException("Unknown event type: " + t);
        }
    }
}