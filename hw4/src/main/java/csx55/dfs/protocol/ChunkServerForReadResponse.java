/* CS555 Distributed Systems - HW4 */
package csx55.dfs.protocol;

/** Response containing a single chunk server address for reading */
public class ChunkServerForReadResponse extends Message {

    private static final long serialVersionUID = 1L;

    private final String chunkServer;

    public ChunkServerForReadResponse(String chunkServer) {
        this.chunkServer = chunkServer;
    }

    @Override
    public MessageType getType() {
        return MessageType.CHUNK_SERVERS_RESPONSE;
    }

    public String getChunkServer() {
        return chunkServer;
    }
}
