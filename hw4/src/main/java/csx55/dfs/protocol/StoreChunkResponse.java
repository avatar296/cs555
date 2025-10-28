/* CS555 Distributed Systems - HW4 */
package csx55.dfs.protocol;

/** Response to store chunk request */
public class StoreChunkResponse extends Message {

    private static final long serialVersionUID = 1L;

    @Override
    public MessageType getType() {
        return MessageType.STORE_CHUNK_RESPONSE;
    }
}
