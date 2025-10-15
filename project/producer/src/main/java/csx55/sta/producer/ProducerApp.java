package csx55.sta.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-Stream Synthetic Data Producer
 * Generates and publishes TripEvents, WeatherEvents, and SpecialEvents
 * to Kafka with independent arrival rates per stream.
 */
public class ProducerApp {
    private static final Logger logger = LoggerFactory.getLogger(ProducerApp.class);

    public static void main(String[] args) {
        SyntheticProducerConfig config = new SyntheticProducerConfig();
        logger.info("Starting Multi-Stream Synthetic Producer");
        logger.info("{}", config);

        List<EventStreamProducer<?>> producers = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();

        // Create enabled stream producers
        if (config.tripConfig.isEnabled()) {
            EventStreamProducer<?> tripProducer = new TripStreamProducer(config.tripConfig, config);
            producers.add(tripProducer);
            Thread tripThread = new Thread(tripProducer, "TripStream");
            threads.add(tripThread);
            logger.info("Trip stream enabled: {}", config.tripConfig);
        }

        if (config.weatherConfig.isEnabled()) {
            EventStreamProducer<?> weatherProducer = new WeatherStreamProducer(config.weatherConfig, config);
            producers.add(weatherProducer);
            Thread weatherThread = new Thread(weatherProducer, "WeatherStream");
            threads.add(weatherThread);
            logger.info("Weather stream enabled: {}", config.weatherConfig);
        }

        if (config.eventConfig.isEnabled()) {
            EventStreamProducer<?> eventProducer = new SpecialEventStreamProducer(config.eventConfig, config);
            producers.add(eventProducer);
            Thread eventThread = new Thread(eventProducer, "EventStream");
            threads.add(eventThread);
            logger.info("Event stream enabled: {}", config.eventConfig);
        }

        if (producers.isEmpty()) {
            logger.error("No streams enabled! At least one stream must have rate > 0");
            System.exit(1);
        }

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down all streams...");
            for (EventStreamProducer<?> producer : producers) {
                producer.stop();
            }
        }));

        // Start all threads
        logger.info("Starting {} stream(s)", threads.size());
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        try {
            for (Thread thread : threads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            logger.warn("Main thread interrupted", e);
            Thread.currentThread().interrupt();
        }

        // Print final statistics
        printFinalStatistics(producers);
    }

    private static void printFinalStatistics(List<EventStreamProducer<?>> producers) {
        logger.info("========================================");
        logger.info("=== FINAL STATISTICS ===");
        logger.info("========================================");

        long totalSent = 0;
        long totalFailed = 0;
        long totalValid = 0;
        long totalInvalid = 0;

        for (EventStreamProducer<?> producer : producers) {
            EventStreamProducer.StreamStatistics stats = producer.getStatistics();

            logger.info("");
            logger.info("=== {} Stream ===", stats.streamName);
            logger.info("Topic: {}", stats.topicName);
            logger.info("Successfully sent:  {}", stats.successCount);
            logger.info("Failed to send:     {}", stats.errorCount);
            logger.info("Valid events:       {} ({} %)",
                    stats.validCount,
                    String.format("%.2f", 100.0 * stats.validCount /
                            Math.max(1, stats.validCount + stats.invalidCount)));
            logger.info("Invalid events:     {} ({} %)",
                    stats.invalidCount,
                    String.format("%.2f", 100.0 * stats.invalidCount /
                            Math.max(1, stats.validCount + stats.invalidCount)));

            totalSent += stats.successCount;
            totalFailed += stats.errorCount;
            totalValid += stats.validCount;
            totalInvalid += stats.invalidCount;
        }

        logger.info("");
        logger.info("=== TOTALS (All Streams) ===");
        logger.info("Total sent:         {}", totalSent);
        logger.info("Total failed:       {}", totalFailed);
        logger.info("Total valid:        {} ({} %)",
                totalValid,
                String.format("%.2f", 100.0 * totalValid / Math.max(1, totalValid + totalInvalid)));
        logger.info("Total invalid:      {} ({} %)",
                totalInvalid,
                String.format("%.2f", 100.0 * totalInvalid / Math.max(1, totalValid + totalInvalid)));
        logger.info("========================================");
    }
}
