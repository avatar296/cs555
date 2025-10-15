package csx55.sta.producer;

import csx55.sta.schema.TripEvent;

import java.util.Random;

/**
 * Generates synthetic NYC Taxi trip events with realistic data distributions.
 */
public class SyntheticTripGenerator {

    private final Random random;
    private final boolean useRealtime;
    private final int timeProgressionSeconds;
    private long simulatedTime;

    // NYC TLC has 263 taxi zones (1-263)
    private static final int MIN_ZONE_ID = 1;
    private static final int MAX_ZONE_ID = 263;

    // Realistic trip parameters
    private static final double MIN_DISTANCE = 0.5;
    private static final double MAX_DISTANCE = 20.0;
    private static final double AVG_DISTANCE = 3.5;
    private static final double DISTANCE_STDDEV = 2.5;

    // Fare calculation (simplified NYC taxi rates)
    private static final double BASE_FARE = 2.50;
    private static final double PER_MILE_RATE = 2.50;
    private static final double SURCHARGE_PROBABILITY = 0.3; // rush hour, etc.
    private static final double SURCHARGE_AMOUNT = 2.50;

    // Passenger distribution (weighted toward 1-2 passengers)
    private static final double[] PASSENGER_WEIGHTS = {0.05, 0.65, 0.20, 0.07, 0.02, 0.01}; // 0-5 passengers

    public SyntheticTripGenerator(boolean useRealtime, int timeProgressionSeconds) {
        this.random = new Random();
        this.useRealtime = useRealtime;
        this.timeProgressionSeconds = timeProgressionSeconds;
        this.simulatedTime = System.currentTimeMillis();
    }

    /**
     * Generate a single synthetic trip event with realistic data.
     */
    public TripEvent generateEvent() {
        long timestamp = useRealtime ? System.currentTimeMillis() : getNextSimulatedTime();
        int pickupLocationId = generateLocationId();
        int dropoffLocationId = generateLocationId();
        double tripDistance = generateTripDistance();
        double fareAmount = calculateFare(tripDistance);
        int passengerCount = generatePassengerCount();

        return TripEvent.newBuilder()
                .setTimestamp(timestamp)
                .setPickupLocationId(pickupLocationId)
                .setDropoffLocationId(dropoffLocationId)
                .setTripDistance(tripDistance)
                .setFareAmount(fareAmount)
                .setPassengerCount(passengerCount)
                .build();
    }

    private long getNextSimulatedTime() {
        long timestamp = simulatedTime;
        simulatedTime += timeProgressionSeconds * 1000L;
        return timestamp;
    }

    private int generateLocationId() {
        // Uniform distribution across all NYC taxi zones
        return MIN_ZONE_ID + random.nextInt(MAX_ZONE_ID - MIN_ZONE_ID + 1);
    }

    private double generateTripDistance() {
        // Normal distribution with realistic mean and stddev
        double distance = AVG_DISTANCE + random.nextGaussian() * DISTANCE_STDDEV;

        // Clamp to realistic bounds
        distance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance));

        // Round to 2 decimal places
        return Math.round(distance * 100.0) / 100.0;
    }

    private double calculateFare(double tripDistance) {
        double fare = BASE_FARE + (tripDistance * PER_MILE_RATE);

        // Add random surcharge (rush hour, tolls, etc.)
        if (random.nextDouble() < SURCHARGE_PROBABILITY) {
            fare += SURCHARGE_AMOUNT;
        }

        // Add some random variance (±5%)
        fare *= (0.95 + random.nextDouble() * 0.10);

        // Round to 2 decimal places
        return Math.round(fare * 100.0) / 100.0;
    }

    private int generatePassengerCount() {
        double rand = random.nextDouble();
        double cumulative = 0.0;

        for (int i = 0; i < PASSENGER_WEIGHTS.length; i++) {
            cumulative += PASSENGER_WEIGHTS[i];
            if (rand < cumulative) {
                return i + 1; // 1-6 passengers
            }
        }

        return 1; // default
    }
}
