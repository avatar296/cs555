package csx55.sta.producer;

import csx55.sta.schema.TripEvent;
import csx55.sta.schema.WeatherEvent;
import csx55.sta.schema.WeatherCondition;
import csx55.sta.schema.SpecialEvent;

import java.util.Random;

/**
 * Injects various types of errors into trip events to simulate bad data
 * for testing error handling and data quality validation.
 */
public class ErrorInjector {

    private final Random random;
    private final double errorRate;

    // Error types and their relative probabilities
    private enum ErrorType {
        NULL_FARE(0.20),              // Nullify optional fareAmount
        NULL_PASSENGERS(0.15),         // Nullify optional passengerCount
        INVALID_PICKUP_ZONE(0.15),     // Set pickup location to invalid zone (0 or > 263)
        INVALID_DROPOFF_ZONE(0.15),    // Set dropoff location to invalid zone
        NEGATIVE_DISTANCE(0.10),       // Negative trip distance
        ZERO_DISTANCE(0.10),           // Zero trip distance
        EXCESSIVE_DISTANCE(0.05),      // Unrealistic high distance (> 100 miles)
        NEGATIVE_FARE(0.05),           // Negative fare amount
        ZERO_PASSENGERS(0.03),         // Zero passengers
        EXCESSIVE_PASSENGERS(0.02);    // Unrealistic passenger count (> 10)

        final double weight;

        ErrorType(double weight) {
            this.weight = weight;
        }
    }

    private static final ErrorType[] ERROR_TYPES = ErrorType.values();
    private static final double TOTAL_WEIGHT;

    static {
        double sum = 0;
        for (ErrorType type : ERROR_TYPES) {
            sum += type.weight;
        }
        TOTAL_WEIGHT = sum;
    }

    public ErrorInjector(double errorRate) {
        if (errorRate < 0.0 || errorRate > 1.0) {
            throw new IllegalArgumentException("Error rate must be between 0.0 and 1.0");
        }
        this.random = new Random();
        this.errorRate = errorRate;
    }

    /**
     * Potentially inject an error into the event based on the error rate.
     * Returns the original event if no error should be injected.
     */
    public TripEvent maybeInjectError(TripEvent event) {
        if (errorRate == 0.0 || random.nextDouble() >= errorRate) {
            return event; // No error injection
        }

        ErrorType errorType = selectErrorType();
        return injectError(event, errorType);
    }

    private ErrorType selectErrorType() {
        double rand = random.nextDouble() * TOTAL_WEIGHT;
        double cumulative = 0.0;

        for (ErrorType type : ERROR_TYPES) {
            cumulative += type.weight;
            if (rand < cumulative) {
                return type;
            }
        }

        return ErrorType.NULL_FARE; // fallback
    }

    private TripEvent injectError(TripEvent event, ErrorType errorType) {
        TripEvent.Builder builder = TripEvent.newBuilder(event);

        switch (errorType) {
            case NULL_FARE:
                builder.setFareAmount(null);
                break;

            case NULL_PASSENGERS:
                builder.setPassengerCount(null);
                break;

            case INVALID_PICKUP_ZONE:
                // Random invalid zone: either 0 or something > 263
                builder.setPickupLocationId(random.nextBoolean() ? 0 : (500 + random.nextInt(500)));
                break;

            case INVALID_DROPOFF_ZONE:
                builder.setDropoffLocationId(random.nextBoolean() ? 0 : (500 + random.nextInt(500)));
                break;

            case NEGATIVE_DISTANCE:
                builder.setTripDistance(-Math.abs(event.getTripDistance()));
                break;

            case ZERO_DISTANCE:
                builder.setTripDistance(0.0);
                break;

            case EXCESSIVE_DISTANCE:
                builder.setTripDistance(100.0 + random.nextDouble() * 400.0); // 100-500 miles
                break;

            case NEGATIVE_FARE:
                if (event.getFareAmount() != null) {
                    builder.setFareAmount(-Math.abs(event.getFareAmount()));
                }
                break;

            case ZERO_PASSENGERS:
                builder.setPassengerCount(0);
                break;

            case EXCESSIVE_PASSENGERS:
                builder.setPassengerCount(11 + random.nextInt(20)); // 11-30 passengers
                break;
        }

        return builder.build();
    }

    /**
     * Check if an event is considered "bad" (has errors)
     */
    public static boolean isInvalidEvent(TripEvent event) {
        // Check for various invalid conditions
        if (event.getPickupLocationId() < 1 || event.getPickupLocationId() > 263) return true;
        if (event.getDropoffLocationId() < 1 || event.getDropoffLocationId() > 263) return true;
        if (event.getTripDistance() <= 0.0) return true;
        if (event.getTripDistance() > 100.0) return true;
        if (event.getFareAmount() != null && event.getFareAmount() < 0.0) return true;
        if (event.getPassengerCount() != null && event.getPassengerCount() <= 0) return true;
        if (event.getPassengerCount() != null && event.getPassengerCount() > 10) return true;

        return false;
    }

    // ==================== Weather Event Error Injection ====================

    /**
     * Potentially inject an error into a weather event
     */
    public WeatherEvent maybeInjectError(WeatherEvent event) {
        if (errorRate == 0.0 || random.nextDouble() >= errorRate) {
            return event;
        }

        WeatherEvent.Builder builder = WeatherEvent.newBuilder(event);
        int errorType = random.nextInt(5);

        switch (errorType) {
            case 0: // Extreme cold temperature
                builder.setTemperature(-100.0 - random.nextDouble() * 50.0);
                break;
            case 1: // Extreme hot temperature
                builder.setTemperature(150.0 + random.nextDouble() * 50.0);
                break;
            case 2: // Negative precipitation
                builder.setPrecipitation(-random.nextDouble() * 5.0);
                break;
            case 3: // Negative wind speed
                builder.setWindSpeed(-random.nextDouble() * 50.0);
                break;
            case 4: // Invalid combination (CLEAR with heavy precipitation)
                builder.setCondition(WeatherCondition.CLEAR);
                builder.setPrecipitation(2.0 + random.nextDouble() * 3.0);
                break;
        }

        return builder.build();
    }

    /**
     * Check if a weather event is invalid
     */
    public static boolean isInvalidEvent(WeatherEvent event) {
        if (event.getLocationId() < 1 || event.getLocationId() > 263) return true;
        if (event.getTemperature() < -50.0 || event.getTemperature() > 150.0) return true;
        if (event.getPrecipitation() < 0.0) return true;
        if (event.getWindSpeed() < 0.0) return true;

        // Invalid combination: CLEAR weather with precipitation
        if (event.getCondition() == WeatherCondition.CLEAR && event.getPrecipitation() > 0.1) return true;
        if (event.getCondition() == WeatherCondition.CLOUDY && event.getPrecipitation() > 0.1) return true;
        if (event.getCondition() == WeatherCondition.FOG && event.getPrecipitation() > 0.1) return true;

        return false;
    }

    // ==================== Special Event Error Injection ====================

    /**
     * Potentially inject an error into a special event
     */
    public SpecialEvent maybeInjectError(SpecialEvent event) {
        if (errorRate == 0.0 || random.nextDouble() >= errorRate) {
            return event;
        }

        SpecialEvent.Builder builder = SpecialEvent.newBuilder(event);
        int errorType = random.nextInt(4);

        switch (errorType) {
            case 0: // Negative attendance
                builder.setAttendanceEstimate(-random.nextInt(10000));
                break;
            case 1: // Unrealistic attendance
                builder.setAttendanceEstimate(100000 + random.nextInt(900000));
                break;
            case 2: // End time before start time
                builder.setEndTime(event.getStartTime() - 3600000); // 1 hour before start
                break;
            case 3: // Invalid location
                builder.setLocationId(random.nextBoolean() ? 0 : (500 + random.nextInt(500)));
                break;
        }

        return builder.build();
    }

    /**
     * Check if a special event is invalid
     */
    public static boolean isInvalidEvent(SpecialEvent event) {
        if (event.getLocationId() < 1 || event.getLocationId() > 263) return true;
        if (event.getAttendanceEstimate() < 0) return true;
        if (event.getAttendanceEstimate() > 100000) return true; // Unrealistic
        if (event.getEndTime() <= event.getStartTime()) return true; // End before/at start

        return false;
    }
}
