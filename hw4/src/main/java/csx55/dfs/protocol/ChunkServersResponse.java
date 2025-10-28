package csx55.dfs.protocol;

import java.util.*;

/**
 * Response from Controller to Client with list of chunk servers
 */
public class ChunkServersResponse extends Message {

    private static final long serialVersionUID = 1L;

    private final List<String> chunkServers; // List of "ip:port"

    public ChunkServersResponse(List<String> chunkServers) {
        this.chunkServers = chunkServers;
    }

    @Override
    public MessageType getType() {
        return MessageType.CHUNK_SERVERS_RESPONSE;
    }

    public List<String> getChunkServers() {
        return chunkServers;
    }
}
