package csx55.sta.producer.config;

public final class NycConstants {

    private NycConstants() {
        // Prevent instantiation
    }

    public static final int MIN_ZONE_ID = 1;
    public static final int MAX_ZONE_ID = 263;
    public static final int ZONE_COUNT = MAX_ZONE_ID - MIN_ZONE_ID + 1;
}
