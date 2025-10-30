package csx55.dfs.protocol;

public class RegisterFileMetadataResponse extends Message {

    private static final long serialVersionUID = 1L;

    private final boolean success;

    public RegisterFileMetadataResponse(boolean success) {
        this.success = success;
    }

    @Override
    public MessageType getType() {
        return MessageType.REGISTER_FILE_METADATA_RESPONSE;
    }

    public boolean isSuccess() {
        return success;
    }
}
