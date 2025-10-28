/* CS555 Distributed Systems - HW4 */
package csx55.dfs.protocol;

/** Enumeration of all message types in the distributed file system protocol */
public enum MessageType {

    // Heartbeat messages (ChunkServer → Controller)
    MAJOR_HEARTBEAT,
    MINOR_HEARTBEAT,
    HEARTBEAT_RESPONSE,

    // Client → Controller requests
    REQUEST_CHUNK_SERVERS_FOR_WRITE,
    REQUEST_CHUNK_SERVER_FOR_READ,
    REQUEST_FILE_INFO,

    // Controller → Client responses
    CHUNK_SERVERS_RESPONSE,
    FILE_INFO_RESPONSE,

    // Client → ChunkServer requests
    STORE_CHUNK_REQUEST,
    READ_CHUNK_REQUEST,
    STORE_FRAGMENT_REQUEST,
    READ_FRAGMENT_REQUEST,

    // ChunkServer → ChunkServer (pipeline forwarding)
    FORWARD_CHUNK_REQUEST,

    // ChunkServer → Client responses
    CHUNK_DATA_RESPONSE,
    STORE_CHUNK_RESPONSE,
    FRAGMENT_DATA_RESPONSE,
    STORE_FRAGMENT_RESPONSE,

    // Error/corruption reporting
    CHUNK_CORRUPTION_REPORT,
    CHUNK_SERVER_FAILURE,

    // Recovery coordination (Controller → ChunkServer)
    REPLICATE_CHUNK_REQUEST,
    RECONSTRUCT_FRAGMENT_REQUEST,

    // General responses
    SUCCESS_RESPONSE,
    ERROR_RESPONSE
}
