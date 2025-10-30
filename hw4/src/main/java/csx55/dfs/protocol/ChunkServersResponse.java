package csx55.dfs.protocol;

import java.util.*;

public class ChunkServersResponse extends Message {

    private static final long serialVersionUID = 1L;

    private final List<String> chunkServers;

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
