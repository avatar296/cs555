package csx55.sta.producer.config;

public class SyntheticProducerConfig {
    public final String kafkaBootstrapServers;
    public final String schemaRegistryUrl;
    public final boolean useRealtime;
    public final int timeProgressionSeconds;
    public final StreamConfig tripConfig;
    public final StreamConfig weatherConfig;
    public final StreamConfig eventConfig;

    public static class StreamConfig {
        public final String topicName;
        public final double arrivalRate; // messages per second (0 = disabled)
        public final double errorRate; // 0.0 to 1.0
        public final long totalEvents; // -1 for infinite

        public StreamConfig(String topicName, double arrivalRate, double errorRate, long totalEvents) {
            this.topicName = topicName;
            this.arrivalRate = arrivalRate;
            this.errorRate = errorRate;
            this.totalEvents = totalEvents;
        }

        public boolean isEnabled() {
            return arrivalRate > 0;
        }

        @Override
        public String toString() {
            if (!isEnabled()) {
                return "DISABLED";
            }
            return String.format("topic=%s, rate=%.1f/s, errors=%.1f%%, total=%s",
                    topicName,
                    arrivalRate,
                    errorRate * 100,
                    totalEvents == -1 ? "infinite" : totalEvents);
        }
    }

    public SyntheticProducerConfig() {
        this.kafkaBootstrapServers = getEnv("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092");
        this.schemaRegistryUrl = getEnv("SCHEMA_REGISTRY_URL", "http://localhost:8082");
        this.useRealtime = Boolean.parseBoolean(getEnv("USE_REALTIME", "true"));
        this.timeProgressionSeconds = Integer.parseInt(getEnv("TIME_PROGRESSION_SECONDS", "1"));
        this.tripConfig = new StreamConfig(
                getEnv("TRIP_TOPIC", "trips.yellow"),
                Double.parseDouble(getEnv("TRIP_RATE", "500")),
                Double.parseDouble(getEnv("TRIP_ERROR_RATE", "0.0")),
                Long.parseLong(getEnv("TRIP_TOTAL_EVENTS", "-1")));

        this.weatherConfig = new StreamConfig(
                getEnv("WEATHER_TOPIC", "weather.updates"),
                Double.parseDouble(getEnv("WEATHER_RATE", "10")),
                Double.parseDouble(getEnv("WEATHER_ERROR_RATE", "0.0")),
                Long.parseLong(getEnv("WEATHER_TOTAL_EVENTS", "-1")));

        this.eventConfig = new StreamConfig(
                getEnv("EVENT_TOPIC", "special.events"),
                Double.parseDouble(getEnv("EVENT_RATE", "0.1")),
                Double.parseDouble(getEnv("EVENT_ERROR_RATE", "0.0")),
                Long.parseLong(getEnv("EVENT_TOTAL_EVENTS", "-1")));

        validate();
    }

    private void validate() {
        validateStream("TRIP", tripConfig);
        validateStream("WEATHER", weatherConfig);
        validateStream("EVENT", eventConfig);

        if (!tripConfig.isEnabled() && !weatherConfig.isEnabled() && !eventConfig.isEnabled()) {
            throw new IllegalArgumentException("At least one stream must be enabled (rate > 0)");
        }
    }

    private void validateStream(String name, StreamConfig config) {
        if (config.arrivalRate < 0) {
            throw new IllegalArgumentException(name + "_RATE must be non-negative, got: " + config.arrivalRate);
        }
        if (config.errorRate < 0.0 || config.errorRate > 1.0) {
            throw new IllegalArgumentException(
                    name + "_ERROR_RATE must be between 0.0 and 1.0, got: " + config.errorRate);
        }
        if (config.totalEvents < -1 || config.totalEvents == 0) {
            throw new IllegalArgumentException(
                    name + "_TOTAL_EVENTS must be positive or -1 for infinite, got: " + config.totalEvents);
        }
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value != null) {
            return value;
        }
        value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    @Override
    public String toString() {
        return "SyntheticProducerConfig{\n" +
                "  Kafka: " + kafkaBootstrapServers + "\n" +
                "  Schema Registry: " + schemaRegistryUrl + "\n" +
                "  Use Realtime: " + useRealtime + "\n" +
                "  Time Progression: " + timeProgressionSeconds + "s\n" +
                "  Trip Stream: " + tripConfig + "\n" +
                "  Weather Stream: " + weatherConfig + "\n" +
                "  Event Stream: " + eventConfig + "\n" +
                '}';
    }
}
