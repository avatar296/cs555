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

# Producer scope & behavior
DATASET        ?= yellow
YEARS          ?= 2024
MONTHS         ?= 06,07,08
RATE           ?= 2000
BATCH          ?= 2000
SALT_KEYS      ?= 8
KEY_MODE       ?= pu
P_LATE         ?= 0.00
LATE_MIN       ?= 300
LATE_MAX       ?= 1200

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
	@echo "  replay          - run DuckDB->Kafka producer (from host)"
	@echo "  spark-consume   - run Spark streaming consumer"
	@echo "  spark-ui        - open Spark Master UI in browser"
	@echo "  clean           - remove venv and caches"
	@echo "  clean-checkpoint - remove Spark checkpoint and state data"
	@echo ""
	@echo "Override: make TOPIC=trips.yellow YEARS=2024 MONTHS=01,02 RATE=5000 replay"

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
	$(PIP) install -r replayer/requirements.txt

# -------- Producer (DuckDB -> Kafka) --------
.PHONY: replay
replay:
	. $(VENV_DIR)/bin/activate && \
	KAFKA_BOOTSTRAP=$(KAFKA_BROKER) \
	TOPIC=$(TOPIC) \
	DATASET=$(DATASET) \
	YEARS="$(YEARS)" \
	MONTHS="$(MONTHS)" \
	RATE=$(RATE) \
	BATCH=$(BATCH) \
	SALT_KEYS=$(SALT_KEYS) \
	KEY_MODE=$(KEY_MODE) \
	P_LATE=$(P_LATE) \
	LATE_MIN=$(LATE_MIN) \
	LATE_MAX=$(LATE_MAX) \
	python replayer/producer.py

# -------- Spark streaming --------
.PHONY: spark-consume spark-ui

spark-consume:
	docker exec spark-master /opt/spark/bin/spark-submit \
		--master spark://spark-master:7077 \
		--packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0,org.apache.spark:spark-avro_2.12:3.5.0,org.apache.hadoop:hadoop-aws:3.3.4 \
		--conf spark.jars.ivy=/tmp/.ivy2 \
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
		--conf spark.sql.streaming.stateStore.providerClass=org.apache.spark.sql.execution.streaming.state.RocksDBStateStoreProvider \
		--conf spark.sql.streaming.stateStore.rocksdb.changelogCheckpointing.enabled=true \
		/opt/spark/work-dir/consumer.py

spark-ui:
	@echo "Opening Spark Master UI..."
	@open http://localhost:8081 2>/dev/null || xdg-open http://localhost:8081 2>/dev/null || echo "Visit http://localhost:8081"

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
