package csx55.sta.producer.generator;

import csx55.sta.producer.config.NycConstants;

import java.util.Random;

/**
 * Abstract base class for synthetic event generators.
 * Provides common functionality for time management, location generation,
 * and utility methods to reduce code duplication across specific generators.
 *
 * @param <T> The type of event this generator produces
 */
public abstract class AbstractSyntheticGenerator<T> {

    protected final Random random;
    protected final boolean useRealtime;
    protected final int timeProgressionSeconds;
    protected long simulatedTime;

    /**
     * Initialize the generator with time configuration.
     *
     * @param useRealtime If true, use current system time. If false, use simulated time progression.
     * @param timeProgressionSeconds For simulated time, how many seconds to advance per event.
     */
    public AbstractSyntheticGenerator(boolean useRealtime, int timeProgressionSeconds) {
        this.random = new Random();
        this.useRealtime = useRealtime;
        this.timeProgressionSeconds = timeProgressionSeconds;
        this.simulatedTime = System.currentTimeMillis();
    }

    /**
     * Generate a single synthetic event.
     * Subclasses must implement this to create domain-specific events.
     *
     * @return A new synthetic event
     */
    public abstract T generateEvent();

    /**
     * Get the timestamp for the next event.
     * Uses either real time or simulated time based on configuration.
     *
     * @return Timestamp in epoch milliseconds
     */
    protected long getNextTimestamp() {
        if (useRealtime) {
            return System.currentTimeMillis();
        } else {
            long timestamp = simulatedTime;
            simulatedTime += timeProgressionSeconds * 1000L;
            return timestamp;
        }
    }

    /**
     * Generate a random NYC taxi zone ID.
     *
     * @return A zone ID between 1 and 263 (inclusive)
     */
    protected int generateLocationId() {
        return NycConstants.MIN_ZONE_ID +
               random.nextInt(NycConstants.ZONE_COUNT);
    }

    /**
     * Select an item from an array using weighted probabilities.
     * The weights array must have the same length as the items array.
     *
     * @param items Array of items to choose from
     * @param weights Array of relative weights (don't need to sum to 1.0)
     * @param <E> Type of items
     * @return The selected item, or the first item if weights are invalid
     */
    protected <E> E selectWeighted(E[] items, double[] weights) {
        if (items.length == 0 || weights.length == 0 || items.length != weights.length) {
            throw new IllegalArgumentException("Items and weights must have same non-zero length");
        }

        // Calculate total weight
        double totalWeight = 0.0;
        for (double weight : weights) {
            totalWeight += weight;
        }

        // Select randomly based on weights
        double rand = random.nextDouble() * totalWeight;
        double cumulative = 0.0;

        for (int i = 0; i < items.length; i++) {
            cumulative += weights[i];
            if (rand < cumulative) {
                return items[i];
            }
        }

        // Fallback (shouldn't reach here due to floating point precision)
        return items[items.length - 1];
    }

    /**
     * Round a value to 1 decimal place.
     *
     * @param value The value to round
     * @return The rounded value
     */
    protected double round1Decimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /**
     * Round a value to 2 decimal places.
     *
     * @param value The value to round
     * @return The rounded value
     */
    protected double round2Decimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Clamp a value between minimum and maximum bounds.
     *
     * @param value The value to clamp
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @return The clamped value
     */
    protected double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Generate a value from a normal (Gaussian) distribution and clamp it to bounds.
     *
     * @param mean The mean of the distribution
     * @param stddev The standard deviation
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @return A random value from the distribution, clamped to [min, max]
     */
    protected double gaussianClamped(double mean, double stddev, double min, double max) {
        double value = mean + random.nextGaussian() * stddev;
        return clamp(value, min, max);
    }
}
