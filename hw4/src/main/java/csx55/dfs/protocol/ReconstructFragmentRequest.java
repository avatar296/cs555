/* CS555 Distributed Systems - HW4 */
package csx55.dfs.protocol;

import java.util.List;

public class ReconstructFragmentRequest extends Message {

    private static final long serialVersionUID = 1L;

    private final String filename;
    private final int chunkNumber;
    private final int fragmentNumber;
    private final List<String> sourceServers;
    private final String targetServer;

    public ReconstructFragmentRequest(
            String filename,
            int chunkNumber,
            int fragmentNumber,
            List<String> sourceServers,
            String targetServer) {
        this.filename = filename;
        this.chunkNumber = chunkNumber;
        this.fragmentNumber = fragmentNumber;
        this.sourceServers = sourceServers;
        this.targetServer = targetServer;
    }

    @Override
    public MessageType getType() {
        return MessageType.RECONSTRUCT_FRAGMENT_REQUEST;
    }

    public String getFilename() {
        return filename;
    }

    public int getChunkNumber() {
        return chunkNumber;
    }

    public int getFragmentNumber() {
        return fragmentNumber;
    }

    public List<String> getSourceServers() {
        return sourceServers;
    }

    public String getTargetServer() {
        return targetServer;
    }
}
