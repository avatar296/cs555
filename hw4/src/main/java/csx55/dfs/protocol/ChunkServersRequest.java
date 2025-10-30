package csx55.dfs.protocol;

public class ChunkServersRequest extends Message {

    private static final long serialVersionUID = 1L;

    private final String filename;
    private final int chunkNumber;
    private final int numServersNeeded;

    public ChunkServersRequest(String filename, int chunkNumber, int numServersNeeded) {
        this.filename = filename;
        this.chunkNumber = chunkNumber;
        this.numServersNeeded = numServersNeeded;
    }

    @Override
    public MessageType getType() {
        return MessageType.REQUEST_CHUNK_SERVERS_FOR_WRITE;
    }

    public String getFilename() {
        return filename;
    }

    public int getChunkNumber() {
        return chunkNumber;
    }

    public int getNumServersNeeded() {
        return numServersNeeded;
    }
}
