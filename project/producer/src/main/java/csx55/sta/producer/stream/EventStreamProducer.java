package csx55.sta.producer.stream;

import csx55.sta.producer.config.SyntheticProducerConfig;
import csx55.sta.producer.util.ErrorInjector;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public abstract class EventStreamProducer<T> implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(EventStreamProducer.class);

    protected final String streamName;
    protected final SyntheticProducerConfig.StreamConfig config;
    protected final SyntheticProducerConfig globalConfig;
    protected final ErrorInjector errorInjector;
    protected final AtomicBoolean shouldStop = new AtomicBoolean(false);
    protected final AtomicLong successCount = new AtomicLong(0);
    protected final AtomicLong errorCount = new AtomicLong(0);
    protected final AtomicLong validEventsGenerated = new AtomicLong(0);
    protected final AtomicLong invalidEventsGenerated = new AtomicLong(0);

    private KafkaProducer<String, T> producer;

    public EventStreamProducer(String streamName,
            SyntheticProducerConfig.StreamConfig config,
            SyntheticProducerConfig globalConfig) {
        this.streamName = streamName;
        this.config = config;
        this.globalConfig = globalConfig;
        this.errorInjector = new ErrorInjector(config.errorRate);
    }

    @Override
    public void run() {
        initializeProducer();

        try {
            logger.info("[{}] Starting stream: topic={}", streamName, config.topicName);
            logger.debug("[{}] Rate={}/s, errorRate={:.1f}%", streamName, config.arrivalRate, config.errorRate * 100);

            long periodNanos = (long) (1_000_000_000.0 / config.arrivalRate);
            long count = 0;
            long startTime = System.nanoTime();
            long lastLogTime = System.currentTimeMillis();

            while (!shouldStop.get() && (config.totalEvents == -1 || count < config.totalEvents)) {
                try {
                    // Generate event
                    T event = generateEvent();

                    // Maybe inject error
                    event = injectError(event);

                    // Track valid vs invalid
                    if (isInvalid(event)) {
                        invalidEventsGenerated.incrementAndGet();
                    } else {
                        validEventsGenerated.incrementAndGet();
                    }

                    // Send to Kafka
                    String key = getPartitionKey(event);
                    ProducerRecord<String, T> record = new ProducerRecord<>(config.topicName, key, event);

                    producer.send(record, (metadata, exception) -> {
                        if (exception != null) {
                            logger.error("[{}] Failed to send record", streamName, exception);
                            errorCount.incrementAndGet();
                        } else {
                            successCount.incrementAndGet();
                        }
                    });

                    count++;

                    // Rate limiting
                    long elapsedNanos = System.nanoTime() - startTime;
                    long expectedNanos = count * periodNanos;
                    long sleepNanos = expectedNanos - elapsedNanos;

                    if (sleepNanos > 0) {
                        TimeUnit.NANOSECONDS.sleep(sleepNanos);
                    }

                    // Progress logging every 10 seconds
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastLogTime >= 10000) {
                        logProgress(count, startTime);
                        lastLogTime = currentTime;
                    }

                } catch (InterruptedException e) {
                    logger.info("[{}] Interrupted, stopping", streamName);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("[{}] Error generating/sending event", streamName, e);
                    errorCount.incrementAndGet();
                }
            }

            producer.flush();
            logger.debug("[{}] Stream complete. Total events: {}", streamName, count);

        } finally {
            if (producer != null) {
                producer.close();
            }
        }
    }

    private void initializeProducer() {
        Properties props = new Properties();
        props.put("bootstrap.servers", globalConfig.kafkaBootstrapServers);
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", KafkaAvroSerializer.class.getName());
        props.put("schema.registry.url", globalConfig.schemaRegistryUrl);
        props.put("acks", "1");
        props.put("compression.type", "snappy");
        props.put("linger.ms", "10");
        props.put("batch.size", "16384");

        // Connection resilience and timeout configurations
        props.put("metadata.max.age.ms", "30000"); // Refresh metadata every 30s
        props.put("connections.max.idle.ms", "540000"); // Keep connections alive (9 min)
        props.put("request.timeout.ms", "30000"); // Fail fast on requests (30s)
        props.put("delivery.timeout.ms", "120000"); // Total time for delivery (2 min)

        // Retry behavior for transient failures
        props.put("retries", "5"); // Retry failed sends up to 5 times
        props.put("retry.backoff.ms", "1000"); // Wait 1s between retries

        this.producer = new KafkaProducer<>(props);
    }

    private void logProgress(long eventsGenerated, long startTime) {
        long elapsedMillis = (System.nanoTime() - startTime) / 1_000_000;
        double actualRate = eventsGenerated / (elapsedMillis / 1000.0);

        logger.info("[{}] Progress: {} events ({}/s), {} sent, {} failed",
                streamName, eventsGenerated, String.format("%.1f", actualRate),
                successCount.get(), errorCount.get());
    }

    public void stop() {
        shouldStop.set(true);
    }

    public StreamStatistics getStatistics() {
        return new StreamStatistics(
                streamName,
                config.topicName,
                successCount.get(),
                errorCount.get(),
                validEventsGenerated.get(),
                invalidEventsGenerated.get());
    }

    // Abstract methods to be implemented by subclasses
    protected abstract T generateEvent();

    protected abstract T injectError(T event);

    protected abstract boolean isInvalid(T event);

    protected abstract String getPartitionKey(T event);

    public static class StreamStatistics {
        public final String streamName;
        public final String topicName;
        public final long successCount;
        public final long errorCount;
        public final long validCount;
        public final long invalidCount;

        public StreamStatistics(String streamName, String topicName, long successCount,
                long errorCount, long validCount, long invalidCount) {
            this.streamName = streamName;
            this.topicName = topicName;
            this.successCount = successCount;
            this.errorCount = errorCount;
            this.validCount = validCount;
            this.invalidCount = invalidCount;
        }
    }
}
