/* CS555 Distributed Systems - HW4 */
package csx55.dfs.protocol;

/**
 * Request for a ChunkServer to replicate a chunk to another ChunkServer Used in failure recovery to
 * restore replication factor
 */
public class ReplicateChunkRequest extends Message {

    private static final long serialVersionUID = 1L;

    private final String filename;
    private final int chunkNumber;
    private final String targetServer; // Server to replicate to (ip:port)

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
