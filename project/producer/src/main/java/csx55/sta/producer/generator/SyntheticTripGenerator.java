package csx55.sta.producer.generator;

import csx55.sta.schema.TripEvent;

/**
 * Generates synthetic NYC Taxi trip events with realistic data distributions.
 */
public class SyntheticTripGenerator extends AbstractSyntheticGenerator<TripEvent> {

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
        super(useRealtime, timeProgressionSeconds);
    }

    /**
     * Generate a single synthetic trip event with realistic data.
     */
    @Override
    public TripEvent generateEvent() {
        long timestamp = getNextTimestamp();
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

    private double generateTripDistance() {
        // Normal distribution with realistic mean and stddev, clamped and rounded
        double distance = gaussianClamped(AVG_DISTANCE, DISTANCE_STDDEV, MIN_DISTANCE, MAX_DISTANCE);
        return round2Decimals(distance);
    }

    private double calculateFare(double tripDistance) {
        double fare = BASE_FARE + (tripDistance * PER_MILE_RATE);

        // Add random surcharge (rush hour, tolls, etc.)
        if (random.nextDouble() < SURCHARGE_PROBABILITY) {
            fare += SURCHARGE_AMOUNT;
        }

        // Add some random variance (±5%)
        fare *= (0.95 + random.nextDouble() * 0.10);

        return round2Decimals(fare);
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
