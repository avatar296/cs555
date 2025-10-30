package csx55.dfs.protocol;

public class StoreChunkResponse extends Message {

    private static final long serialVersionUID = 1L;

    @Override
    public MessageType getType() {
        return MessageType.STORE_CHUNK_RESPONSE;
    }
}
