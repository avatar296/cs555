# NYC Taxi Spatial Temporal Analytics - Medallion

Real-time spatial-temporal analysis of NYC taxi data using Apache Spark Structured Streaming, Apache Iceberg, and a multi-layer lakehouse architecture.

## Architecture

The system implements a **Medallion Architecture** with Bronze, Silver, and Gold layers:

### Data Flow

1. **Producer** generates synthetic taxi trips, weather readings, and special events
2. **Bronze Layer** ingests raw Avro data from Kafka into Iceberg tables (partitioned)
3. **Silver Layer** validates, cleans, enriches data with derived features (rush hour flags, categories)
4. **Gold Layer** performs real-time aggregations (5-min windows) and maintains dimension tables
5. **Monitoring Layer** captures data quality metrics and end-to-end pipeline latency

## Project Structure

```
project/
├── common/                           Avro schemas (TripEvent, WeatherEvent, SpecialEvent)
├── producer/                         Multi-stream synthetic data generator
├── lakehouse/
│   ├── streaming/                    Shared base classes, config, session builders
│   ├── schema-management/            DDL management and table setup utilities
│   ├── bronze/                       Raw Kafka → Iceberg ingestion jobs
│   │   ├── jobs/                     Trip/Weather/Event consumer jobs
│   │   └── resources/                Configuration files
│   ├── silver/                       Data cleaning, validation, enrichment
│   │   ├── batch/                    Batch processing with Deequ validation
│   │   ├── jobs/                     Streaming transformation jobs
│   │   ├── resources/sql/            Transformation SQL queries
│   │   └── resources/ddl/            Table DDL definitions
│   └── gold/                         Analytics aggregations and dimensions
│       ├── jobs/                     Live metrics, dimension loaders
│       ├── resources/sql/            Aggregation SQL queries
│       └── resources/ddl/            Gold table DDL definitions
└── infra/
    ├── docker-compose.yml            Infrastructure services
    └── spark/                        Custom Spark Docker image
```

## Technology Stack

- **Apache Spark 3.5.0** - Structured Streaming engine
- **Apache Iceberg** - Lakehouse table format with ACID transactions
- **Apache Kafka** - Event streaming platform (Avro serialization)
- **Apache Avro** - Schema evolution and serialization
- **Amazon Deequ** - Data quality validation framework
- **PostgreSQL** - Iceberg catalog metadata store
- **MinIO** - S3-compatible object storage
- **Java 17** & **Scala 2.12**
- **Gradle** (Kotlin DSL) - Build system
- **Spotless** - Code formatting

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Java 17
- Gradle 8.x (or use `./gradlew`)

### 1. Build All Modules

```bash
./gradlew clean :lakehouse:schema-management:jar \
                :lakehouse:bronze:jar \
                :lakehouse:silver:jar \
                :lakehouse:gold:jar \
                :producer:jar
```

### 2. Start Infrastructure

```bash
cd infra
docker-compose up -d postgres-iceberg minio kafka schema-registry
```

Wait for services to initialize (~30 seconds), then:

```bash
docker-compose up -d minio-setup spark-master spark-worker
```

### 3. Initialize Lakehouse Tables

```bash
docker-compose up bronze-table-setup
docker-compose up silver-table-setup
docker-compose up gold-table-setup
```

### 4. Start Data Producer

```bash
docker-compose up -d producer
```

### 5. Start Streaming Jobs

```bash
docker-compose up -d bronze-trips-consumer bronze-weather-consumer bronze-events-consumer
docker-compose up -d silver-trips-consumer silver-weather-consumer silver-events-consumer
docker-compose up -d gold-trip-metrics
```

### 6. Load Dimension Tables (Optional)

```bash
docker-compose run --rm gold-time-dim-loader
docker-compose run --rm gold-location-dim-loader
```

## Service Endpoints

| Service | URL | Credentials |
|---------|-----|-------------|
| Spark Master UI | http://localhost:8080 | - |
| Spark Worker UI | http://localhost:8081 | - |
| MinIO Console | http://localhost:9001 | admin/admin123 |
| Kafka UI | http://localhost:8088 | - |
| Schema Registry | http://localhost:8081 | - |

## Project Commands

### Build Commands

```bash
./gradlew clean build
./gradlew :lakehouse:bronze:jar
./gradlew :lakehouse:silver:jar
./gradlew :lakehouse:gold:jar
./gradlew spotlessCheck
./gradlew spotlessApply
```

## Configuration

All configuration is environment-based via Docker Compose. Key settings:

- **Kafka Bootstrap Servers**: `kafka:9092`
- **Schema Registry URL**: `http://schema-registry:8081`
- **Iceberg Catalog**: JDBC (PostgreSQL)
- **Warehouse Path**: `s3a://lakehouse/warehouse`
- **Checkpoint Location**: `/tmp/checkpoint/<layer>/<job>`
- **Trigger Interval**: 10 seconds (configurable)

