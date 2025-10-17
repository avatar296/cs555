package csx55.sta.producer.config;

/**
 * Constants related to NYC Taxi zones and geographic data.
 * NYC TLC (Taxi and Limousine Commission) defines 263 taxi zones across the five boroughs.
 */
public final class NycConstants {

    private NycConstants() {
        // Prevent instantiation
    }

    /**
     * Minimum valid NYC TLC taxi zone ID
     */
    public static final int MIN_ZONE_ID = 1;

    /**
     * Maximum valid NYC TLC taxi zone ID
     */
    public static final int MAX_ZONE_ID = 263;

    /**
     * Total number of NYC taxi zones
     */
    public static final int ZONE_COUNT = MAX_ZONE_ID - MIN_ZONE_ID + 1;
}
