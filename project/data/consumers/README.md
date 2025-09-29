# Bronze Layer Consumers - Medallion Architecture

## Overview

The bronze layer consumers implement the first stage of the medallion architecture, ingesting raw data from Kafka topics and persisting it to Iceberg tables with minimal transformation. The key principle is to preserve the raw data exactly as received, partitioned by **ingestion timestamp** rather than event time.

## Architecture

```
Kafka Topics → Bronze Consumers → Iceberg Tables (Bronze Layer)
    ↓                                     ↓
trips.yellow                        bronze.trips
weather.noaa                        bronze.weather
events.nyc                          bronze.events
```

## Key Design Decisions

### 1. Ingestion-Time Partitioning
- Bronze tables are partitioned by `kafka_timestamp` (when we received the data)
- Partitioning: `year/month/day/hour` based on ingestion time
- This ensures consistent write patterns and avoids issues with late-arriving data

### 2. Schema Preservation
- All original Avro fields are preserved without modification
- Kafka metadata is added for lineage tracking:
  - `kafka_timestamp`, `kafka_partition`, `kafka_offset`, `kafka_topic`
  - `consumer_id`, `bronze_processing_time`

### 3. Data Quality Flags
- Quality checks are performed but data is NOT filtered
- Quality flags are added as new columns (e.g., `is_valid_distance`, `quality_score`)
- Bad data is preserved for analysis and debugging

## Running Bronze Consumers

### Individual Consumers
```bash
# Start trips consumer
make bronze-trips

# Start weather consumer
make bronze-weather

# Start events consumer
make bronze-events
```

### All Consumers in Parallel
```bash
make bronze-all
```

### Configuration Options
```bash
# Override default settings
make bronze-trips BRONZE_STARTING_OFFSETS=earliest BRONZE_TRIGGER_INTERVAL="10 seconds"

# Check status
make bronze-status

# Clean bronze layer data
make bronze-clean
```

## What to Adjust Going Forward

### 1. Performance Tuning
- **Trigger Interval**: Currently 30 seconds for trips, 60 seconds for weather/events
  - Adjust based on data volume and latency requirements
- **Max Offsets Per Trigger**: Currently 10000 for trips, 5000 for weather/events
  - Increase for higher throughput, decrease for lower latency
- **Parallelism**: Add more Spark executors for higher volume

### 2. Schema Evolution
- Schema changes are handled automatically via Schema Registry
- Bronze consumers will adapt to new fields in Avro schemas
- Consider versioning strategy for breaking changes

### 3. Error Handling
- Currently, malformed records cause streaming to fail
- Consider implementing dead letter queue (DLQ) for bad records:
  - Use `iceberg_writer.create_dead_letter_table()`
  - Write failed records to separate DLQ table

### 4. Monitoring & Alerting
- Add metrics collection for:
  - Consumer lag
  - Records processed per batch
  - Data quality scores
- Integrate with monitoring system (Prometheus, Datadog, etc.)

### 5. Compaction Strategy
- Small files will accumulate over time
- Schedule regular compaction jobs:
  ```python
  from data.consumers.core.iceberg_writer import compact_bronze_tables
  compact_bronze_tables(spark, "bronze", ["bronze.trips", "bronze.weather", "bronze.events"])
  ```

### 6. Retention Policy
- Implement data retention based on business requirements
- Use Iceberg's time travel features for historical queries
- Schedule snapshot expiration:
  ```python
  writer.expire_snapshots("bronze.trips", older_than_days=30)
  ```

## Next Steps: Silver Layer

The silver layer should:
1. Read from bronze tables (not Kafka directly)
2. Apply business logic transformations
3. Handle late-arriving data using event time
4. Perform data cleansing and normalization
5. Join data streams where appropriate

Example silver layer transformations:
- Convert timestamps to consistent timezone
- Normalize location IDs across datasets
- Calculate derived metrics (speed, duration, etc.)
- Apply business rules for data quality

## Testing

To test the bronze consumers:

1. Start infrastructure:
   ```bash
   make up
   ```

2. Start producers to generate data:
   ```bash
   make produce-trips RATE=100
   make produce-weather RATE=10
   make produce-events RATE=10
   ```

3. Start bronze consumers:
   ```bash
   make bronze-all
   ```

4. Verify data in Iceberg:
   ```bash
   # Check tables exist
   make bronze-status

   # Query with DuckDB (requires setup)
   python query_duckdb.py
   ```

## Troubleshooting

### Consumer Lag
If consumers fall behind:
- Increase `max_offsets_per_trigger`
- Decrease `trigger_interval`
- Add more Spark executors

### Memory Issues
- Reduce batch size with `max_offsets_per_trigger`
- Increase executor memory in Spark config

### Schema Registry Connection
- Ensure Schema Registry is running: `docker ps | grep schema-registry`
- Check URL configuration matches docker-compose setup

### Iceberg Write Failures
- Check MinIO is running and accessible
- Verify S3 credentials in consumer configuration
- Check disk space in MinIO container