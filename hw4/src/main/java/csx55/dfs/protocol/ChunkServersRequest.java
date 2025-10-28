/* CS555 Distributed Systems - HW4 */
package csx55.dfs.protocol;

/** Request from Client to Controller to get chunk servers for writing a chunk */
public class ChunkServersRequest extends Message {

    private static final long serialVersionUID = 1L;

    private final String filename;
    private final int chunkNumber;
    private final int numServersNeeded; // 3 for replication, 9 for erasure coding

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
