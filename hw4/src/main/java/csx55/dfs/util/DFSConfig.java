package csx55.dfs.util;

public class DFSConfig {

    public static final int CHUNK_SIZE = 64 * 1024;

    public static final int DATA_SHARDS = 6;
    public static final int PARITY_SHARDS = 3;
    public static final int TOTAL_SHARDS = 9;

    public static final int MINOR_HEARTBEAT_INTERVAL = 15000;
    public static final int MAJOR_HEARTBEAT_INTERVAL = 60000;

    public static final int FAILURE_DETECTION_INTERVAL = 10000;
    public static final long FAILURE_THRESHOLD = 180000;

    private DFSConfig() {}
}
