package csx55.dfs.protocol;

public class ChunkServerForReadRequest extends Message {

    private static final long serialVersionUID = 1L;

    private final String filename;
    private final int chunkNumber;

    public ChunkServerForReadRequest(String filename, int chunkNumber) {
        this.filename = filename;
        this.chunkNumber = chunkNumber;
    }

    @Override
    public MessageType getType() {
        return MessageType.REQUEST_CHUNK_SERVER_FOR_READ;
    }

    public String getFilename() {
        return filename;
    }

    public int getChunkNumber() {
        return chunkNumber;
    }
}
