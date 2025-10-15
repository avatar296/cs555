package csx55.sta.producer;

import csx55.sta.schema.WeatherCondition;
import csx55.sta.schema.WeatherEvent;

import java.util.Random;

/**
 * Generates synthetic weather events with realistic patterns.
 */
public class SyntheticWeatherGenerator {

    private final Random random;
    private final boolean useRealtime;
    private final int timeProgressionSeconds;
    private long simulatedTime;

    // NYC TLC has 263 taxi zones
    private static final int MIN_ZONE_ID = 1;
    private static final int MAX_ZONE_ID = 263;

    // Temperature ranges (Fahrenheit)
    private static final double MIN_TEMP = 10.0;   // Cold winter
    private static final double MAX_TEMP = 95.0;   // Hot summer
    private static final double AVG_TEMP = 60.0;   // Annual average
    private static final double TEMP_STDDEV = 15.0;

    // Weather condition probabilities
    private static final double[] CONDITION_WEIGHTS = {
            0.40,  // CLEAR
            0.30,  // CLOUDY
            0.15,  // RAIN
            0.08,  // SNOW
            0.05,  // FOG
            0.02   // STORM
    };

    public SyntheticWeatherGenerator(boolean useRealtime, int timeProgressionSeconds) {
        this.random = new Random();
        this.useRealtime = useRealtime;
        this.timeProgressionSeconds = timeProgressionSeconds;
        this.simulatedTime = System.currentTimeMillis();
    }

    /**
     * Generate a synthetic weather event.
     */
    public WeatherEvent generateEvent() {
        long timestamp = useRealtime ? System.currentTimeMillis() : getNextSimulatedTime();
        int locationId = generateLocationId();
        double temperature = generateTemperature();
        WeatherCondition condition = generateCondition();
        double precipitation = generatePrecipitation(condition);
        double windSpeed = generateWindSpeed(condition);

        return WeatherEvent.newBuilder()
                .setTimestamp(timestamp)
                .setLocationId(locationId)
                .setTemperature(temperature)
                .setPrecipitation(precipitation)
                .setWindSpeed(windSpeed)
                .setCondition(condition)
                .build();
    }

    private long getNextSimulatedTime() {
        long timestamp = simulatedTime;
        simulatedTime += timeProgressionSeconds * 1000L;
        return timestamp;
    }

    private int generateLocationId() {
        return MIN_ZONE_ID + random.nextInt(MAX_ZONE_ID - MIN_ZONE_ID + 1);
    }

    private double generateTemperature() {
        // Normal distribution around average
        double temp = AVG_TEMP + random.nextGaussian() * TEMP_STDDEV;
        temp = Math.max(MIN_TEMP, Math.min(MAX_TEMP, temp));
        return Math.round(temp * 10.0) / 10.0;
    }

    private WeatherCondition generateCondition() {
        double rand = random.nextDouble();
        double cumulative = 0.0;

        WeatherCondition[] conditions = WeatherCondition.values();
        for (int i = 0; i < CONDITION_WEIGHTS.length && i < conditions.length; i++) {
            cumulative += CONDITION_WEIGHTS[i];
            if (rand < cumulative) {
                return conditions[i];
            }
        }

        return WeatherCondition.CLEAR;
    }

    private double generatePrecipitation(WeatherCondition condition) {
        double precip = 0.0;

        switch (condition) {
            case CLEAR:
            case CLOUDY:
            case FOG:
                precip = 0.0;
                break;
            case RAIN:
                // Light to moderate rain: 0.1 to 0.5 inches/hour
                precip = 0.1 + random.nextDouble() * 0.4;
                break;
            case SNOW:
                // Light to moderate snow: 0.05 to 0.3 inches/hour
                precip = 0.05 + random.nextDouble() * 0.25;
                break;
            case STORM:
                // Heavy precipitation: 0.5 to 2.0 inches/hour
                precip = 0.5 + random.nextDouble() * 1.5;
                break;
        }

        return Math.round(precip * 100.0) / 100.0;
    }

    private double generateWindSpeed(WeatherCondition condition) {
        double windSpeed;

        switch (condition) {
            case CLEAR:
            case CLOUDY:
                // Calm to light breeze: 0-10 mph
                windSpeed = random.nextDouble() * 10.0;
                break;
            case RAIN:
            case SNOW:
            case FOG:
                // Light to moderate wind: 5-20 mph
                windSpeed = 5.0 + random.nextDouble() * 15.0;
                break;
            case STORM:
                // Strong winds: 20-50 mph
                windSpeed = 20.0 + random.nextDouble() * 30.0;
                break;
            default:
                windSpeed = random.nextDouble() * 10.0;
        }

        return Math.round(windSpeed * 10.0) / 10.0;
    }
}
