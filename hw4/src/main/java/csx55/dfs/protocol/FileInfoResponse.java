package csx55.dfs.protocol;

public class FileInfoResponse extends Message {

    private static final long serialVersionUID = 1L;

    private final int numChunks;

    public FileInfoResponse(int numChunks) {
        this.numChunks = numChunks;
    }

    @Override
    public MessageType getType() {
        return MessageType.FILE_INFO_RESPONSE;
    }

    public int getNumChunks() {
        return numChunks;
    }
}
