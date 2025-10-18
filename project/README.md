# NYC Taxi Streaming Analytics - Medallion Architecture

Spatial-temporal analysis of NYC taxi data using Apache Kafka, Spark, and Iceberg with Bronze-Silver-Gold layers.

## Architecture

```
Kafka (Avro) → Bronze Layer → Silver Layer → Gold Layer
                    ↓              ↓             ↓
                 Raw Data    Cleaned Data   Aggregations
```

### Project Structure

```
sta/
├── common/                    Avro schemas (TripEvent, WeatherEvent, SpecialEvent)
├── streaming-common/          Shared base classes, config, utilities
├── producer/                  Synthetic data generator
├── bronze-layer/              Raw Kafka → Iceberg ingestion
├── silver-layer/              Data cleaning & enrichment (scaffold)
└── gold-layer/                Analytics aggregations (scaffold)
```

## Quick Start

### 1. Build
```bash
./gradlew :bronze-layer:jar
```

### 2. Start Services
```bash
docker-compose up -d
```

Services:
- Kafka UI: http://localhost:8080
- Spark UI: http://localhost:8081
- MinIO Console: http://localhost:9001 (admin/admin123)

### 3. Submit Bronze Layer Jobs
```bash
./submit-bronze-layer.sh
```

This starts 3 streaming jobs:
- `TripsBronzeJob`: trips.yellow → lakehouse.bronze.trips
- `WeatherBronzeJob`: weather.updates → lakehouse.bronze.weather
- `EventsBronzeJob`: special.events → lakehouse.bronze.events

## Key Features

### Bronze Layer
- **Auto Avro deserialization** from Schema Registry
- **Exactly-once** processing with checkpoints
- **Metadata enrichment**: kafka offsets, ingestion timestamps
- **90% code reduction** via base classes

### Configuration
All settings in `streaming-common/src/main/resources/application.conf`:
```hocon
bronze {
  trips {
    topic = "trips.yellow"
    table = "lakehouse.bronze.trips"
    checkpoint = "/tmp/checkpoint/bronze/trips"
  }
}
```

Override with environment variables: `KAFKA_BOOTSTRAP_SERVERS`, `S3_ENDPOINT`, etc.

## Code Comparison

**Before** (322 lines duplicated):
```java
public class TripsBronzeStream {
    // 108 lines of Spark/Kafka/Iceberg setup
    // Hardcoded configuration
    // Duplicated across 3 jobs
}
```

**After** (20 lines per job):
```java
public class TripsBronzeJob extends AbstractBronzeJob {
    public TripsBronzeJob(StreamConfig config) {
        super(config, config.getBronzeTripsConfig());
    }

    protected String getJobName() { return "TripsBronzeJob"; }

    public static void main(String[] args) throws Exception {
        new TripsBronzeJob(new StreamConfig()).run();
    }
}
```

## Monitoring

```bash
# Spark logs
docker logs -f spark-master

# Query Iceberg tables
docker exec -it spark-master /opt/spark/bin/spark-sql \
  --conf spark.sql.catalog.lakehouse=org.apache.iceberg.spark.SparkCatalog \
  --conf spark.sql.catalog.lakehouse.type=hadoop \
  --conf spark.sql.catalog.lakehouse.warehouse=s3a://lakehouse/warehouse

spark-sql> SELECT COUNT(*) FROM lakehouse.bronze.trips;
```

## Next Steps

1. **Silver Layer**: Implement cleaning and enrichment jobs
2. **Gold Layer**: Implement aggregation jobs
3. **Data Quality**: Add validation and monitoring
4. **CI/CD**: Automated testing and deployment

## Documentation

- `MEDALLION_ARCHITECTURE.md` - Full architecture details
- `REORGANIZATION_SUMMARY.md` - Refactoring summary
