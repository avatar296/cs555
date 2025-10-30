/* CS555 Distributed Systems - HW4 */
package csx55.dfs.protocol;

/** Response to a ReplicateChunkRequest indicating success or failure */
public class ReplicateChunkResponse extends Message {

    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String errorMessage;

    public ReplicateChunkResponse(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public ReplicateChunkResponse(boolean success) {
        this(success, null);
    }

    @Override
    public MessageType getType() {
        return MessageType.REPLICATE_CHUNK_RESPONSE;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
