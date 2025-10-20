package csx55.sta.producer;

import csx55.sta.producer.config.SyntheticProducerConfig;
import csx55.sta.producer.stream.EventStreamProducer;
import csx55.sta.producer.stream.SpecialEventStreamProducer;
import csx55.sta.producer.stream.TripStreamProducer;
import csx55.sta.producer.stream.WeatherStreamProducer;
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
            logger.info("Trip stream enabled");
        }

        if (config.weatherConfig.isEnabled()) {
            EventStreamProducer<?> weatherProducer = new WeatherStreamProducer(config.weatherConfig, config);
            producers.add(weatherProducer);
            Thread weatherThread = new Thread(weatherProducer, "WeatherStream");
            threads.add(weatherThread);
            logger.info("Weather stream enabled");
        }

        if (config.eventConfig.isEnabled()) {
            EventStreamProducer<?> eventProducer = new SpecialEventStreamProducer(config.eventConfig, config);
            producers.add(eventProducer);
            Thread eventThread = new Thread(eventProducer, "EventStream");
            threads.add(eventThread);
            logger.info("Event stream enabled");
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
        logger.info("Final Statistics:");

        long totalSent = 0;
        long totalFailed = 0;
        long totalValid = 0;
        long totalInvalid = 0;

        for (EventStreamProducer<?> producer : producers) {
            EventStreamProducer.StreamStatistics stats = producer.getStatistics();
            logger.info("{}: {} sent, {} failed, {} valid, {} invalid",
                    stats.streamName, stats.successCount, stats.errorCount,
                    stats.validCount, stats.invalidCount);

            totalSent += stats.successCount;
            totalFailed += stats.errorCount;
            totalValid += stats.validCount;
            totalInvalid += stats.invalidCount;
        }

        logger.info("Total: {} sent, {} failed, {} valid, {} invalid",
                totalSent, totalFailed, totalValid, totalInvalid);
    }
}
