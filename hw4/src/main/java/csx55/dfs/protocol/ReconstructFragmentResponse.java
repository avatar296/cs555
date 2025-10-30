/* CS555 Distributed Systems - HW4 */
package csx55.dfs.protocol;

public class ReconstructFragmentResponse extends Message {

    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String errorMessage;

    public ReconstructFragmentResponse(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public ReconstructFragmentResponse(boolean success) {
        this(success, null);
    }

    @Override
    public MessageType getType() {
        return MessageType.RECONSTRUCT_FRAGMENT_RESPONSE;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
