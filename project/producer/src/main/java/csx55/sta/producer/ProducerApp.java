package csx55.sta.producer;

import csx55.sta.producer.config.SyntheticProducerConfig;
import csx55.sta.producer.stream.EventStreamProducer;
import csx55.sta.producer.stream.SpecialEventStreamProducer;
import csx55.sta.producer.stream.TripStreamProducer;
import csx55.sta.producer.stream.WeatherStreamProducer;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProducerApp {
  private static final Logger logger = LoggerFactory.getLogger(ProducerApp.class);

  public static void main(String[] args) {
    SyntheticProducerConfig config = new SyntheticProducerConfig();
    logger.info("Starting Multi-Stream Synthetic Producer");
    logger.info("{}", config);

    List<EventStreamProducer<?>> producers = new ArrayList<>();
    List<Thread> threads = new ArrayList<>();

    if (config.tripConfig.isEnabled()) {
      EventStreamProducer<?> tripProducer = new TripStreamProducer(config.tripConfig, config);
      producers.add(tripProducer);
      Thread tripThread = new Thread(tripProducer, "TripStream");
      threads.add(tripThread);
      logger.info("Trip stream enabled");
    }

    if (config.weatherConfig.isEnabled()) {
      EventStreamProducer<?> weatherProducer =
          new WeatherStreamProducer(config.weatherConfig, config);
      producers.add(weatherProducer);
      Thread weatherThread = new Thread(weatherProducer, "WeatherStream");
      threads.add(weatherThread);
      logger.info("Weather stream enabled");
    }

    if (config.eventConfig.isEnabled()) {
      EventStreamProducer<?> eventProducer =
          new SpecialEventStreamProducer(config.eventConfig, config);
      producers.add(eventProducer);
      Thread eventThread = new Thread(eventProducer, "EventStream");
      threads.add(eventThread);
      logger.info("Event stream enabled");
    }

    if (producers.isEmpty()) {
      logger.error("No streams enabled! At least one stream must have rate > 0");
      System.exit(1);
    }

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  logger.info("Shutting down all streams...");
                  for (EventStreamProducer<?> producer : producers) {
                    producer.stop();
                  }
                }));

    logger.info("Starting {} stream(s)", threads.size());
    for (Thread thread : threads) {
      thread.start();
    }

    try {
      for (Thread thread : threads) {
        thread.join();
      }
    } catch (InterruptedException e) {
      logger.warn("Main thread interrupted", e);
      Thread.currentThread().interrupt();
    }

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
      logger.info(
          "{}: {} sent, {} failed, {} valid, {} invalid",
          stats.streamName,
          stats.successCount,
          stats.errorCount,
          stats.validCount,
          stats.invalidCount);

      totalSent += stats.successCount;
      totalFailed += stats.errorCount;
      totalValid += stats.validCount;
      totalInvalid += stats.invalidCount;
    }

    logger.info(
        "Total: {} sent, {} failed, {} valid, {} invalid",
        totalSent,
        totalFailed,
        totalValid,
        totalInvalid);
  }
}
