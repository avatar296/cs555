/* CS555 Distributed Systems - HW4 */
package csx55.dfs.util;

import java.io.Serializable;

public class ChunkMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String filename;
    private final int chunkNumber;
    private int version;
    private final int sequenceNumber;
    private long timestamp;
    private final int dataSize;

    public ChunkMetadata(String filename, int chunkNumber, int dataSize) {
        this.filename = filename;
        this.chunkNumber = chunkNumber;
        this.version = 1;
        this.sequenceNumber = chunkNumber;
        this.timestamp = System.currentTimeMillis();
        this.dataSize = dataSize;
    }

    // Getters
    public String getFilename() {
        return filename;
    }

    public int getChunkNumber() {
        return chunkNumber;
    }

    public int getVersion() {
        return version;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getDataSize() {
        return dataSize;
    }

    @Override
    public String toString() {
        return String.format(
                "ChunkMetadata{file=%s, chunk=%d, version=%d, seq=%d, size=%d, time=%d}",
                filename, chunkNumber, version, sequenceNumber, dataSize, timestamp);
    }
}
