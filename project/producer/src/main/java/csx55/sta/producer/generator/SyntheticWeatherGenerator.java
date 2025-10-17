package csx55.sta.producer.generator;

import csx55.sta.schema.WeatherCondition;
import csx55.sta.schema.WeatherEvent;

/**
 * Generates synthetic weather events with realistic patterns.
 */
public class SyntheticWeatherGenerator extends AbstractSyntheticGenerator<WeatherEvent> {

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
        super(useRealtime, timeProgressionSeconds);
    }

    /**
     * Generate a synthetic weather event.
     */
    @Override
    public WeatherEvent generateEvent() {
        long timestamp = getNextTimestamp();
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

    private double generateTemperature() {
        double temp = gaussianClamped(AVG_TEMP, TEMP_STDDEV, MIN_TEMP, MAX_TEMP);
        return round1Decimal(temp);
    }

    private WeatherCondition generateCondition() {
        return selectWeighted(WeatherCondition.values(), CONDITION_WEIGHTS);
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

        return round2Decimals(precip);
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

        return round1Decimal(windSpeed);
    }
}
