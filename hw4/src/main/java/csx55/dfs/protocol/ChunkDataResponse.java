package csx55.dfs.protocol;

public class ChunkDataResponse extends Message {

    private static final long serialVersionUID = 1L;

    private final String filename;
    private final int chunkNumber;
    private final byte[] data;
    private final boolean success;
    private final String errorMessage;

    public ChunkDataResponse(String filename, int chunkNumber, byte[] data) {
        this.filename = filename;
        this.chunkNumber = chunkNumber;
        this.data = data;
        this.success = true;
        this.errorMessage = null;
    }

    public ChunkDataResponse(String filename, int chunkNumber, String errorMessage) {
        this.filename = filename;
        this.chunkNumber = chunkNumber;
        this.data = null;
        this.success = false;
        this.errorMessage = errorMessage;
    }

    @Override
    public MessageType getType() {
        return MessageType.CHUNK_DATA_RESPONSE;
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

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
