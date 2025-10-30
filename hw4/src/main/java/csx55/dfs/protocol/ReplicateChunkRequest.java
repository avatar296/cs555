package csx55.dfs.protocol;

public class ReplicateChunkRequest extends Message {

    private static final long serialVersionUID = 1L;

    private final String filename;
    private final int chunkNumber;
    private final String targetServer;

    public ReplicateChunkRequest(String filename, int chunkNumber, String targetServer) {
        this.filename = filename;
        this.chunkNumber = chunkNumber;
        this.targetServer = targetServer;
    }

    @Override
    public MessageType getType() {
        return MessageType.REPLICATE_CHUNK_REQUEST;
    }

    public String getFilename() {
        return filename;
    }

    public int getChunkNumber() {
        return chunkNumber;
    }

    public String getTargetServer() {
        return targetServer;
    }
}
