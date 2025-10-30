/* CS555 Distributed Systems - HW4 */
package csx55.dfs.util;

import java.io.Serializable;

public class FragmentMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String filename;
    private final int chunkNumber;
    private final int fragmentNumber;
    private int version;
    private final int sequenceNumber;
    private long timestamp;
    private final int dataSize;

    public FragmentMetadata(String filename, int chunkNumber, int fragmentNumber, int dataSize) {
        this.filename = filename;
        this.chunkNumber = chunkNumber;
        this.fragmentNumber = fragmentNumber;
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

    public int getFragmentNumber() {
        return fragmentNumber;
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
                "FragmentMetadata{file=%s, chunk=%d, fragment=%d, version=%d, seq=%d, size=%d,"
                        + " time=%d}",
                filename,
                chunkNumber,
                fragmentNumber,
                version,
                sequenceNumber,
                dataSize,
                timestamp);
    }
}
