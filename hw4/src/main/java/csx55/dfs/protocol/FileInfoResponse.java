package csx55.dfs.protocol;

public class FileInfoResponse extends Message {

    private static final long serialVersionUID = 1L;

    private final int numChunks;
    private final long fileSize;

    public FileInfoResponse(int numChunks, long fileSize) {
        this.numChunks = numChunks;
        this.fileSize = fileSize;
    }

    @Override
    public MessageType getType() {
        return MessageType.FILE_INFO_RESPONSE;
    }

    public int getNumChunks() {
        return numChunks;
    }

    public long getFileSize() {
        return fileSize;
    }
}
