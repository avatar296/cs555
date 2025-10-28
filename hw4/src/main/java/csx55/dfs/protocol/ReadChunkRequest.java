package csx55.dfs.protocol;

/**
 * Request to read a chunk from a chunk server
 */
public class ReadChunkRequest extends Message {

    private static final long serialVersionUID = 1L;

    private final String filename;
    private final int chunkNumber;

    public ReadChunkRequest(String filename, int chunkNumber) {
        this.filename = filename;
        this.chunkNumber = chunkNumber;
    }

    @Override
    public MessageType getType() {
        return MessageType.READ_CHUNK_REQUEST;
    }

    public String getFilename() {
        return filename;
    }

    public int getChunkNumber() {
        return chunkNumber;
    }
}
