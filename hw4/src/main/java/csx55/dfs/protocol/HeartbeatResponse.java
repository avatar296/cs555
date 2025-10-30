/* CS555 Distributed Systems - HW4 */
package csx55.dfs.protocol;

public class HeartbeatResponse extends Message {

    private static final long serialVersionUID = 1L;

    @Override
    public MessageType getType() {
        return MessageType.HEARTBEAT_RESPONSE;
    }
}
