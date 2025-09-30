# =========================
# CityScale (local Compose)
# =========================

COMPOSE        ?= docker compose
PYTHON         ?= python3
PIP            ?= pip
VENV_DIR       ?= .venv

# Kafka / Topic
KAFKA_BROKER   ?= localhost:29092    # from host; use kafka:9092 inside containers
TOPIC          ?= trips.yellow
PARTITIONS     ?= 12
REPLICATION    ?= 1

# Producer scope & behavior (unified configuration for all producers)
DATASET        ?= yellow      # For trips producer
YEARS          ?= 2023        # Used by all producers (standardized)
MONTHS         ?= 06,07,08    # Used by all producers (standardized)
RATE           ?= 2000        # Messages per second (all producers)
BATCH          ?= 2000        # Batch size (all producers)
BURST_MODE     ?= false       # Set to true for max throughput (no realistic delays)
SALT_KEYS      ?= 8           # Key salting for partitioning (all producers)
KEY_MODE       ?= default     # Key generation mode (all producers)
P_LATE         ?= 0.00        # Lateness injection probability (all producers)
LATE_MIN       ?= 300         # Min lateness seconds (all producers)
LATE_MAX       ?= 1200        # Max lateness seconds (all producers)

# Synthetic data configuration (unified for all producers)
SYNTHETIC_MODE ?= fallback    # none|fallback|mixed|synthetic (all producers)
SYNTHETIC_RATIO ?= 0.2        # For mixed mode: ratio of synthetic data (all producers)

# -------- Help --------
.PHONY: help
help:
	@echo "Targets:"
	@echo "  up              - start Kafka and Spark services"
	@echo "  down            - stop services and remove volumes"
	@echo "  logs            - tail compose logs"
	@echo "  kafka-topics    - list all Kafka topics"
	@echo "  kafka-describe  - describe topic details (default: trips.yellow)"
	@echo "  kafka-offsets   - show partition offsets for topic"
	@echo "  kafka-delete-topic - delete a topic"
	@echo "  schema-register - register/update Avro schemas in Schema Registry"
	@echo "  schema-list     - list all registered schemas"
	@echo "  install         - create venv and install producer dependencies"
	@echo "  produce-trips   - run taxi trips producer (unified config)"
	@echo "  produce-weather - run weather data producer (unified config)"
	@echo "  produce-events  - run events data producer (unified config)"
	@echo "  start-producers - start all producers in background"
	@echo "  stop-producers  - stop all running producers"
	@echo "  producer-status - show status of running producers"
	@echo "  spark-consume   - run Spark streaming consumer"
	@echo "  spark-ui        - open Spark Master UI in browser"
	@echo "  bronze-trips    - run bronze layer trips consumer"
	@echo "  bronze-weather  - run bronze layer weather consumer"
	@echo "  bronze-events   - run bronze layer events consumer"
	@echo "  bronze-all      - run all bronze consumers in parallel"
	@echo "  bronze-status   - check bronze consumer and table status"
	@echo "  bronze-clean    - clean bronze layer data and checkpoints"
	@echo "  silver-trips    - run silver layer trips consumer"
	@echo "  silver-status   - check silver consumer and table status"
	@echo "  silver-clean    - clean silver layer data and checkpoints"
	@echo "  clean           - remove venv and caches"
	@echo "  clean-checkpoint - remove Spark checkpoint and state data"
	@echo ""
	@echo "Examples:"
	@echo "  make produce-trips SYNTHETIC_MODE=synthetic"
	@echo "  make produce-weather YEARS=2024 MONTHS=01,02 RATE=500"
	@echo "  make produce-events P_LATE=0.1 SALT_KEYS=10"
	@echo ""
	@echo "Real-time Pipeline (default):"
	@echo "  Bronze: 2 second triggers (Kafka -> Iceberg)"
	@echo "  Silver: 5 second triggers (Iceberg -> Iceberg)"
	@echo "  End-to-end latency: 5-10 seconds"
	@echo ""
	@echo "Override for batch-like: make SILVER_TRIGGER_INTERVAL='60 seconds' silver-trips"

# -------- Compose lifecycle --------
.PHONY: up down logs
up:
	$(COMPOSE) up -d --wait --wait-timeout 30 || true
	@echo "Waiting for Schema Registry to be ready..."
	@sleep 5
	$(MAKE) schema-register

down:
	$(COMPOSE) down -v

logs:
	$(COMPOSE) logs -f --tail=200

# -------- Kafka utilities --------
.PHONY: kafka-topics kafka-describe kafka-offsets kafka-delete-topic

kafka-topics:
	docker exec kafka /opt/bitnami/kafka/bin/kafka-topics.sh \
		--bootstrap-server localhost:9092 --list

kafka-describe:
	docker exec kafka /opt/bitnami/kafka/bin/kafka-topics.sh \
		--bootstrap-server localhost:9092 \
		--describe --topic $(TOPIC)

kafka-offsets:
	@echo "Getting consumer groups and offsets..."
	@docker exec kafka /opt/bitnami/kafka/bin/kafka-consumer-groups.sh \
		--bootstrap-server localhost:9092 \
		--all-groups \
		--describe || echo "No active consumer groups found"

kafka-delete-topic:
	docker exec kafka /opt/bitnami/kafka/bin/kafka-topics.sh \
		--bootstrap-server localhost:9092 \
		--delete --topic $(TOPIC)

# -------- Schema Registry --------
.PHONY: schema-register schema-list

schema-register:
	. $(VENV_DIR)/bin/activate && \
	python schemas/register_schemas.py

schema-list:
	@echo "Registered schemas:"
	@curl -s http://localhost:8082/subjects | python -m json.tool || echo "Schema Registry not available"

# -------- Python env (producer) --------
.PHONY: venv install
venv:
	$(PYTHON) -m venv $(VENV_DIR)
	@echo "Run: source $(VENV_DIR)/bin/activate"

install: venv
	. $(VENV_DIR)/bin/activate && \
	$(PIP) install --upgrade pip && \
	$(PIP) install -r requirements.txt

# -------- Data Producers --------
.PHONY: produce-trips produce-weather produce-events start-producers stop-producers producer-status

produce-trips:
	. $(VENV_DIR)/bin/activate && \
	cd data && \
	KAFKA_BOOTSTRAP=$(KAFKA_BROKER) \
	TOPIC=$(TOPIC) \
	DATASET=$(DATASET) \
	YEARS="$(YEARS)" \
	MONTHS="$(MONTHS)" \
	RATE=$(RATE) \
	BATCH=$(BATCH) \
	BURST_MODE=$(BURST_MODE) \
	SALT_KEYS=$(SALT_KEYS) \
	KEY_MODE=$(KEY_MODE) \
	P_LATE=$(P_LATE) \
	LATE_MIN=$(LATE_MIN) \
	LATE_MAX=$(LATE_MAX) \
	SYNTHETIC_MODE=$(SYNTHETIC_MODE) \
	SYNTHETIC_RATIO=$(SYNTHETIC_RATIO) \
	python -m producers.run_producer trips

produce-weather:
	. $(VENV_DIR)/bin/activate && \
	cd data && \
	KAFKA_BOOTSTRAP=$(KAFKA_BROKER) \
	TOPIC=weather.updates \
	YEARS="$(YEARS)" \
	MONTHS="$(MONTHS)" \
	RATE=$(RATE) \
	BATCH=$(BATCH) \
	BURST_MODE=$(BURST_MODE) \
	SALT_KEYS=$(SALT_KEYS) \
	KEY_MODE=$(KEY_MODE) \
	P_LATE=$(P_LATE) \
	LATE_MIN=$(LATE_MIN) \
	LATE_MAX=$(LATE_MAX) \
	SYNTHETIC_MODE=$(SYNTHETIC_MODE) \
	SYNTHETIC_RATIO=$(SYNTHETIC_RATIO) \
	python -m producers.run_producer weather

produce-events:
	. $(VENV_DIR)/bin/activate && \
	cd data && \
	KAFKA_BOOTSTRAP=$(KAFKA_BROKER) \
	TOPIC=events.calendar \
	YEARS="$(YEARS)" \
	MONTHS="$(MONTHS)" \
	RATE=$(RATE) \
	BATCH=$(BATCH) \
	BURST_MODE=$(BURST_MODE) \
	SALT_KEYS=$(SALT_KEYS) \
	KEY_MODE=$(KEY_MODE) \
	P_LATE=$(P_LATE) \
	LATE_MIN=$(LATE_MIN) \
	LATE_MAX=$(LATE_MAX) \
	SYNTHETIC_MODE=$(SYNTHETIC_MODE) \
	SYNTHETIC_RATIO=$(SYNTHETIC_RATIO) \
	python -m producers.run_producer events

start-producers:
	@echo "Starting all data producers..."
	$(MAKE) produce-trips &
	$(MAKE) produce-weather &
	$(MAKE) produce-events &
	@echo "All producers started in background"
	@echo "Use 'make producer-status' to see running producers"
	@echo "Use 'make stop-producers' to stop all producers"

stop-producers:
	@echo "Stopping all Python producers..."
	@pkill -f "python -m producers.run_producer trips" || true
	@pkill -f "python -m producers.run_producer weather" || true
	@pkill -f "python -m producers.run_producer events" || true
	@echo "All producers stopped"

producer-status:
	@echo "Active Python producers:"
	@ps aux | grep -E "python -m producers.run_producer (trips|weather|events)" | grep -v grep || echo "No producers running"
	@echo ""
	@echo "Kafka topic status:"
	@docker exec kafka /opt/bitnami/kafka/bin/kafka-consumer-groups.sh \
		--bootstrap-server localhost:9092 \
		--all-groups --describe 2>/dev/null | head -20 || echo "No active consumers"

# -------- Spark streaming --------
.PHONY: spark-consume spark-ui

spark-consume:
	docker exec spark-master /opt/spark/bin/spark-submit \
		--master spark://spark-master:7077 \
		--packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0,org.apache.spark:spark-avro_2.12:3.5.0,org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.2,org.apache.hadoop:hadoop-aws:3.3.4 \
		--conf spark.jars.ivy=/tmp/.ivy2 \
		--conf spark.sql.extensions=org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions \
		--conf spark.sql.catalog.spark_catalog=org.apache.iceberg.spark.SparkSessionCatalog \
		--conf spark.sql.catalog.spark_catalog.type=hive \
		--conf spark.sql.catalog.local=org.apache.iceberg.spark.SparkCatalog \
		--conf spark.sql.catalog.local.type=hadoop \
		--conf spark.sql.catalog.local.warehouse=s3a://lakehouse/iceberg \
		--conf spark.hadoop.fs.s3a.endpoint=http://minio:9000 \
		--conf spark.hadoop.fs.s3a.access.key=admin \
		--conf spark.hadoop.fs.s3a.secret.key=admin123 \
		--conf spark.hadoop.fs.s3a.path.style.access=true \
		--conf spark.hadoop.fs.s3a.impl=org.apache.hadoop.fs.s3a.S3AFileSystem \
		--conf spark.hadoop.fs.s3a.connection.ssl.enabled=false \
		--conf spark.executor.instances=2 \
		--conf spark.executor.cores=2 \
		--conf spark.executor.memory=1g \
		--conf spark.dynamicAllocation.enabled=false \
		/opt/spark/work-dir/consumer.py

spark-ui:
	@echo "Opening Spark Master UI..."
	@open http://localhost:8081 2>/dev/null || xdg-open http://localhost:8081 2>/dev/null || echo "Visit http://localhost:8081"

# -------- Bronze Layer Consumers (Medallion Architecture) --------
.PHONY: bronze-trips bronze-weather bronze-events bronze-all bronze-status bronze-clean

# Bronze consumer configuration (can be overridden)
BRONZE_KAFKA_BOOTSTRAP ?= kafka:9092
BRONZE_SCHEMA_REGISTRY ?= http://schema-registry:8081
BRONZE_STARTING_OFFSETS ?= latest
BRONZE_TRIGGER_INTERVAL ?= 2 seconds  # Real-time: very low latency from Kafka
BRONZE_MAX_OFFSETS ?= 10000

bronze-trips:
	@echo "Starting Bronze Trips Consumer..."
	docker exec -it spark-master /opt/spark/bin/spark-submit \
		--packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0,org.apache.spark:spark-avro_2.12:3.5.0,org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.2,org.apache.hadoop:hadoop-aws:3.3.4 \
		--conf spark.jars.ivy=/tmp/.ivy2 \
		--conf spark.sql.adaptive.enabled=false \
		--conf spark.sql.streaming.stateStore.stateSchemaCheck=false \
		--conf spark.sql.catalog.bronze=org.apache.iceberg.spark.SparkCatalog \
		--conf spark.sql.catalog.bronze.type=hadoop \
		--conf spark.sql.catalog.bronze.warehouse=s3a://lakehouse/iceberg \
		--conf spark.hadoop.fs.s3a.endpoint=http://minio:9000 \
		--conf spark.hadoop.fs.s3a.access.key=admin \
		--conf spark.hadoop.fs.s3a.secret.key=admin123 \
		--conf spark.hadoop.fs.s3a.path.style.access=true \
		/opt/spark/work-dir/consumers/run_bronze_consumer.py \
		--source trips \
		--kafka-bootstrap $(BRONZE_KAFKA_BOOTSTRAP) \
		--schema-registry $(BRONZE_SCHEMA_REGISTRY) \
		--starting-offsets $(BRONZE_STARTING_OFFSETS)

bronze-weather:
	@echo "Starting Bronze Weather Consumer..."
	docker exec -it spark-master /opt/spark/bin/spark-submit \
		--packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0,org.apache.spark:spark-avro_2.12:3.5.0,org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.2,org.apache.hadoop:hadoop-aws:3.3.4 \
		--conf spark.jars.ivy=/tmp/.ivy2 \
		--conf spark.sql.adaptive.enabled=false \
		--conf spark.sql.streaming.stateStore.stateSchemaCheck=false \
		--conf spark.sql.catalog.bronze=org.apache.iceberg.spark.SparkCatalog \
		--conf spark.sql.catalog.bronze.type=hadoop \
		--conf spark.sql.catalog.bronze.warehouse=s3a://lakehouse/iceberg \
		--conf spark.hadoop.fs.s3a.endpoint=http://minio:9000 \
		--conf spark.hadoop.fs.s3a.access.key=admin \
		--conf spark.hadoop.fs.s3a.secret.key=admin123 \
		--conf spark.hadoop.fs.s3a.path.style.access=true \
		/opt/spark/work-dir/consumers/run_bronze_consumer.py \
		--source weather \
		--kafka-bootstrap $(BRONZE_KAFKA_BOOTSTRAP) \
		--schema-registry $(BRONZE_SCHEMA_REGISTRY) \
		--starting-offsets $(BRONZE_STARTING_OFFSETS)

bronze-events:
	@echo "Starting Bronze Events Consumer..."
	docker exec -it spark-master /opt/spark/bin/spark-submit \
		--packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0,org.apache.spark:spark-avro_2.12:3.5.0,org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.2,org.apache.hadoop:hadoop-aws:3.3.4 \
		--conf spark.jars.ivy=/tmp/.ivy2 \
		--conf spark.sql.adaptive.enabled=false \
		--conf spark.sql.streaming.stateStore.stateSchemaCheck=false \
		--conf spark.sql.catalog.bronze=org.apache.iceberg.spark.SparkCatalog \
		--conf spark.sql.catalog.bronze.type=hadoop \
		--conf spark.sql.catalog.bronze.warehouse=s3a://lakehouse/iceberg \
		--conf spark.hadoop.fs.s3a.endpoint=http://minio:9000 \
		--conf spark.hadoop.fs.s3a.access.key=admin \
		--conf spark.hadoop.fs.s3a.secret.key=admin123 \
		--conf spark.hadoop.fs.s3a.path.style.access=true \
		/opt/spark/work-dir/consumers/run_bronze_consumer.py \
		--source events \
		--kafka-bootstrap $(BRONZE_KAFKA_BOOTSTRAP) \
		--schema-registry $(BRONZE_SCHEMA_REGISTRY) \
		--starting-offsets $(BRONZE_STARTING_OFFSETS)

bronze-all:
	@echo "Starting All Bronze Consumers in Parallel..."
	docker exec -it spark-master /opt/spark/bin/spark-submit \
		--packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0,org.apache.spark:spark-avro_2.12:3.5.0,org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.2,org.apache.hadoop:hadoop-aws:3.3.4 \
		--conf spark.jars.ivy=/tmp/.ivy2 \
		--conf spark.sql.adaptive.enabled=false \
		--conf spark.sql.streaming.stateStore.stateSchemaCheck=false \
		--conf spark.sql.catalog.bronze=org.apache.iceberg.spark.SparkCatalog \
		--conf spark.sql.catalog.bronze.type=hadoop \
		--conf spark.sql.catalog.bronze.warehouse=s3a://lakehouse/iceberg \
		--conf spark.hadoop.fs.s3a.endpoint=http://minio:9000 \
		--conf spark.hadoop.fs.s3a.access.key=admin \
		--conf spark.hadoop.fs.s3a.secret.key=admin123 \
		--conf spark.hadoop.fs.s3a.path.style.access=true \
		--conf spark.executor.instances=3 \
		--conf spark.executor.cores=2 \
		/opt/spark/work-dir/consumers/run_bronze_consumer.py \
		--all \
		--kafka-bootstrap $(BRONZE_KAFKA_BOOTSTRAP) \
		--schema-registry $(BRONZE_SCHEMA_REGISTRY) \
		--starting-offsets $(BRONZE_STARTING_OFFSETS) \
		--parallel

bronze-status:
	@echo "Checking bronze consumer status..."
	@echo ""
	@echo "Kafka consumer groups:"
	@docker exec kafka /opt/bitnami/kafka/bin/kafka-consumer-groups.sh \
		--bootstrap-server localhost:9092 \
		--all-groups \
		--describe 2>/dev/null | grep -E "bronze|GROUP" || echo "No bronze consumers found"
	@echo ""
	@echo "MinIO Iceberg bronze tables:"
	@docker exec minio mc ls minio/lakehouse/iceberg/bronze/ 2>/dev/null || echo "No bronze tables found"

bronze-clean:
	@echo "Cleaning bronze layer checkpoints and data..."
	@docker exec spark-master rm -rf /tmp/checkpoint/bronze/* 2>/dev/null || true
	@docker exec minio mc rm -r minio/lakehouse/iceberg/bronze/ 2>/dev/null || true
	@echo "Bronze layer cleaned"

# -------- Silver Layer Consumers (Medallion Architecture) --------
.PHONY: silver-trips silver-weather silver-events silver-status silver-clean

# Silver consumer configuration (can be overridden)
# Real-time configuration: 5 second triggers, 10 minute watermarks
SILVER_QUALITY_THRESHOLD ?= 0.7
SILVER_TRIGGER_INTERVAL ?= 5 seconds   # Real-time: 5-10 second latency
SILVER_WATERMARK ?= 10 minutes         # Real-time: shorter watermark for late data
SILVER_STARTING_OFFSETS ?= latest

silver-trips:
	@echo "Starting Silver Trips Consumer..."
	docker exec -it spark-master /opt/spark/bin/spark-submit \
		--packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0,org.apache.spark:spark-avro_2.12:3.5.0,org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.2,org.apache.hadoop:hadoop-aws:3.3.4 \
		--conf spark.jars.ivy=/tmp/.ivy2 \
		--conf spark.sql.adaptive.enabled=false \
		--conf spark.sql.streaming.stateStore.stateSchemaCheck=false \
		--conf spark.sql.catalog.bronze=org.apache.iceberg.spark.SparkCatalog \
		--conf spark.sql.catalog.bronze.type=hadoop \
		--conf spark.sql.catalog.bronze.warehouse=s3a://lakehouse/iceberg \
		--conf spark.sql.catalog.silver=org.apache.iceberg.spark.SparkCatalog \
		--conf spark.sql.catalog.silver.type=hadoop \
		--conf spark.sql.catalog.silver.warehouse=s3a://lakehouse/iceberg \
		--conf spark.hadoop.fs.s3a.endpoint=http://minio:9000 \
		--conf spark.hadoop.fs.s3a.access.key=admin \
		--conf spark.hadoop.fs.s3a.secret.key=admin123 \
		--conf spark.hadoop.fs.s3a.path.style.access=true \
		/opt/spark/work-dir/consumers/run_silver_consumer.py \
		--source trips \
		--quality-threshold $(SILVER_QUALITY_THRESHOLD) \
		--trigger-interval "$(SILVER_TRIGGER_INTERVAL)" \
		--starting-offsets $(SILVER_STARTING_OFFSETS)

silver-weather:
	@echo "Silver Weather Consumer - Not Yet Implemented"
	@echo "To implement: Create data/consumers/silver/weather.py"

silver-events:
	@echo "Silver Events Consumer - Not Yet Implemented"
	@echo "To implement: Create data/consumers/silver/events.py"

silver-status:
	@echo "Checking silver consumer status..."
	@echo ""
	@echo "Silver checkpoint status:"
	@docker exec spark-master ls -la /tmp/checkpoint/silver/ 2>/dev/null || echo "No silver checkpoints found"
	@echo ""
	@echo "MinIO Iceberg silver tables:"
	@docker exec minio mc ls minio/lakehouse/iceberg/silver/ 2>/dev/null || echo "No silver tables found"

silver-clean:
	@echo "Cleaning silver layer checkpoints and data..."
	@docker exec spark-master rm -rf /tmp/checkpoint/silver/* 2>/dev/null || true
	@docker exec minio mc rm -r minio/lakehouse/iceberg/silver/ 2>/dev/null || true
	@echo "Silver layer cleaned"

# -------- DuckDB Query Engine --------
.PHONY: query-duckdb install-duckdb

install-duckdb:
	@echo "Installing DuckDB and dependencies..."
	pip install -r requirements-query.txt

query-duckdb:
	@echo "Querying Iceberg tables with DuckDB..."
	python query_duckdb.py

# -------- Clean up --------
.PHONY: clean clean-checkpoint

clean:
	rm -rf $(VENV_DIR)
	find . -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true
	find . -type f -name "*.pyc" -delete 2>/dev/null || true

clean-checkpoint:
	@echo "Cleaning Spark checkpoint and state data..."
	docker exec spark-master rm -rf /opt/spark/work-dir/checkpoint/* 2>/dev/null || true
	docker exec spark-master rm -rf /tmp/checkpoint/* 2>/dev/null || true
	docker exec spark-master rm -rf /tmp/state-store/* 2>/dev/null || true
	@echo "Checkpoint data cleared"
