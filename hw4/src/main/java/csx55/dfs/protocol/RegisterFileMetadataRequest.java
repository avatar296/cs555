package csx55.dfs.protocol;

public class RegisterFileMetadataRequest extends Message {

    private static final long serialVersionUID = 1L;

    private final String filename;
    private final long fileSize;

    public RegisterFileMetadataRequest(String filename, long fileSize) {
        this.filename = filename;
        this.fileSize = fileSize;
    }

    @Override
    public MessageType getType() {
        return MessageType.REGISTER_FILE_METADATA_REQUEST;
    }

    public String getFilename() {
        return filename;
    }

    public long getFileSize() {
        return fileSize;
    }
}
