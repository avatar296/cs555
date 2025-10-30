package csx55.dfs.protocol;

public class FileInfoRequest extends Message {

    private static final long serialVersionUID = 1L;

    private final String filename;

    public FileInfoRequest(String filename) {
        this.filename = filename;
    }

    @Override
    public MessageType getType() {
        return MessageType.REQUEST_FILE_INFO;
    }

    public String getFilename() {
        return filename;
    }
}
