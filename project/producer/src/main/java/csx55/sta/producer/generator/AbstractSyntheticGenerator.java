package csx55.sta.producer.generator;

import csx55.sta.producer.config.NycConstants;

import java.util.Random;

public abstract class AbstractSyntheticGenerator<T> {

    protected final Random random;
    protected final boolean useRealtime;
    protected final int timeProgressionSeconds;
    protected long simulatedTime;

    public AbstractSyntheticGenerator(boolean useRealtime, int timeProgressionSeconds) {
        this.random = new Random();
        this.useRealtime = useRealtime;
        this.timeProgressionSeconds = timeProgressionSeconds;
        this.simulatedTime = System.currentTimeMillis();
    }

    public abstract T generateEvent();

    protected long getNextTimestamp() {
        if (useRealtime) {
            return System.currentTimeMillis();
        } else {
            long timestamp = simulatedTime;
            simulatedTime += timeProgressionSeconds * 1000L;
            return timestamp;
        }
    }

    protected int generateLocationId() {
        return NycConstants.MIN_ZONE_ID +
                random.nextInt(NycConstants.ZONE_COUNT);
    }

    protected <E> E selectWeighted(E[] items, double[] weights) {
        if (items.length == 0 || weights.length == 0 || items.length != weights.length) {
            throw new IllegalArgumentException("Items and weights must have same non-zero length");
        }

        double totalWeight = 0.0;
        for (double weight : weights) {
            totalWeight += weight;
        }

        double rand = random.nextDouble() * totalWeight;
        double cumulative = 0.0;

        for (int i = 0; i < items.length; i++) {
            cumulative += weights[i];
            if (rand < cumulative) {
                return items[i];
            }
        }

        return items[items.length - 1];
    }

    protected double round1Decimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    protected double round2Decimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    protected double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    protected double gaussianClamped(double mean, double stddev, double min, double max) {
        double value = mean + random.nextGaussian() * stddev;
        return clamp(value, min, max);
    }
}
