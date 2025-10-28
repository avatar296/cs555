package csx55.dfs.util;

import java.io.Serializable;

/**
 * Metadata associated with a chunk
 *
 * Includes:
 * - Versioning information (incremented on writes)
 * - Sequence number
 * - Timestamp
 * - File information
 */
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
        this.sequenceNumber = chunkNumber; // Sequence number matches chunk number
        this.timestamp = System.currentTimeMillis();
        this.dataSize = dataSize;
    }

    public ChunkMetadata(String filename, int chunkNumber, int version, int sequenceNumber,
                        long timestamp, int dataSize) {
        this.filename = filename;
        this.chunkNumber = chunkNumber;
        this.version = version;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
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

    // Update operations
    public void incrementVersion() {
        this.version++;
        this.timestamp = System.currentTimeMillis();
    }

    public void updateTimestamp() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Get unique key for this chunk
     */
    public String getKey() {
        return filename + ":" + chunkNumber;
    }

    @Override
    public String toString() {
        return String.format("ChunkMetadata{file=%s, chunk=%d, version=%d, seq=%d, size=%d, time=%d}",
                filename, chunkNumber, version, sequenceNumber, dataSize, timestamp);
    }
}
