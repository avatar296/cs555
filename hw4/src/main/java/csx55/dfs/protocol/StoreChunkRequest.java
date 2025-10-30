/* CS555 Distributed Systems - HW4 */
package csx55.dfs.protocol;

import java.util.*;

public class StoreChunkRequest extends Message {

    private static final long serialVersionUID = 1L;

    private final String filename;
    private final int chunkNumber;
    private final byte[] data;
    private final List<String> nextServers;

    public StoreChunkRequest(
            String filename, int chunkNumber, byte[] data, List<String> nextServers) {
        this.filename = filename;
        this.chunkNumber = chunkNumber;
        this.data = data;
        this.nextServers = nextServers;
    }

    @Override
    public MessageType getType() {
        return MessageType.STORE_CHUNK_REQUEST;
    }

    public String getFilename() {
        return filename;
    }

    public int getChunkNumber() {
        return chunkNumber;
    }

    public byte[] getData() {
        return data;
    }

    public List<String> getNextServers() {
        return nextServers;
    }
}
