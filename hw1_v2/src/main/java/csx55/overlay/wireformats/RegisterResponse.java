package csx55.overlay.wireformats;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class RegisterResponse implements Event {
    public static final byte SUCCESS = 1;
    public static final byte FAILURE = 0;

    private final byte status;
    private final String info;

    public RegisterResponse(byte status, String info) {
        this.status = status;
        this.info = info;
    }

    public RegisterResponse(DataInputStream in) throws IOException {
        this.status = in.readByte();
        this.info = in.readUTF();
    }

    public byte status() {
        return status;
    }

    public String info() {
        return info;
    }

    @Override
    public int type() {
        return Protocol.REGISTER_RESPONSE;
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeInt(type());
        out.writeByte(status);
        out.writeUTF(info);
    }
}
