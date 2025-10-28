/* CS555 Distributed Systems - HW4 */
package csx55.dfs.protocol;

import java.util.*;

/**
 * Heartbeat message sent from ChunkServer to Controller
 *
 * <p>Two types: - Major (every 60s): Contains ALL chunk metadata - Minor (every 15s): Contains only
 * newly added chunks
 */
public class HeartbeatMessage extends Message {

    private static final long serialVersionUID = 1L;

    private final MessageType type; // MAJOR_HEARTBEAT or MINOR_HEARTBEAT
    private final String chunkServerId; // "ip:port"
    private final int totalChunks;
    private final long freeSpace; // bytes
    private final List<ChunkInfo> chunks; // Chunk metadata

    public HeartbeatMessage(
            MessageType type,
            String chunkServerId,
            int totalChunks,
            long freeSpace,
            List<ChunkInfo> chunks) {
        this.type = type;
        this.chunkServerId = chunkServerId;
        this.totalChunks = totalChunks;
        this.freeSpace = freeSpace;
        this.chunks = chunks;
    }

    @Override
    public MessageType getType() {
        return type;
    }

    public String getChunkServerId() {
        return chunkServerId;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public long getFreeSpace() {
        return freeSpace;
    }

    public List<ChunkInfo> getChunks() {
        return chunks;
    }

    /** Information about a chunk or fragment */
    public static class ChunkInfo implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        public String filename;
        public int chunkNumber;
        public int version;
        public int sequenceNumber;
        public long timestamp;
        public int dataSize;

        // For erasure coding mode
        public Integer fragmentNumber; // null for replication mode

        public ChunkInfo(
                String filename,
                int chunkNumber,
                int version,
                int sequenceNumber,
                long timestamp,
                int dataSize) {
            this.filename = filename;
            this.chunkNumber = chunkNumber;
            this.version = version;
            this.sequenceNumber = sequenceNumber;
            this.timestamp = timestamp;
            this.dataSize = dataSize;
            this.fragmentNumber = null;
        }

        public ChunkInfo(
                String filename,
                int chunkNumber,
                int fragmentNumber,
                int version,
                int sequenceNumber,
                long timestamp,
                int dataSize) {
            this.filename = filename;
            this.chunkNumber = chunkNumber;
            this.version = version;
            this.sequenceNumber = sequenceNumber;
            this.timestamp = timestamp;
            this.dataSize = dataSize;
            this.fragmentNumber = fragmentNumber;
        }
    }
}
