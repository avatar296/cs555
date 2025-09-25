# =========================
# CityScale (local Compose)
# =========================

COMPOSE        ?= docker compose
PYTHON         ?= python
PIP            ?= pip
VENV_DIR       ?= .venv

# Kafka / Topic
KAFKA_BROKER   ?= localhost:9092    # from host; use kafka:9092 inside containers
TOPIC          ?= trips.yellow
PARTITIONS     ?= 12
REPLICATION    ?= 1

# Spark
SPARK_SUBMIT   ?= spark-submit
SPARK_MASTER   ?= spark://localhost:7077   # Spark master from host
SPARK_PACKAGES ?= org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1,io.delta:delta-spark_2.12:3.2.0

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

# Local output paths (since MinIO is commented out)
CHK_DIR        ?= /tmp/cityscale/chk
DELTA_PU       ?= /tmp/cityscale/delta/pu_hour
DELTA_OD       ?= /tmp/cityscale/delta/od_hour

# -------- Help --------
.PHONY: help
help:
	@echo "Targets:"
	@echo "  up              - start services"
	@echo "  up10            - start services and scale to 10 Spark workers"
	@echo "  down            - stop services"
	@echo "  logs            - tail compose logs"
	@echo "  create-topic    - create Kafka topic ($(TOPIC))"
	@echo "  venv install    - create venv and install replayer deps"
	@echo "  replay          - run DuckDB->Kafka producer (from host)"
	@echo "  stream          - run Spark consumer (file:// sink under /tmp)"
	@echo "  clean           - remove venv and caches"
	@echo ""
	@echo "Override: make TOPIC=trips.yellow YEARS=2024 MONTHS=01,02 RATE=5000 replay"

# -------- Compose lifecycle --------
.PHONY: up up10 down logs
up:
	$(COMPOSE) up -d --wait

up10: up
	$(COMPOSE) up -d --scale spark-worker=10

down:
	$(COMPOSE) down -v

logs:
	$(COMPOSE) logs -f --tail=200

# -------- Kafka topic --------
.PHONY: create-topic
create-topic:
	docker run --rm --network host confluentinc/cp-kafka:7.6.1 \
	  kafka-topics --bootstrap-server $(KAFKA_BROKER) \
	  --create --if-not-exists --topic $(TOPIC) \
	  --partitions $(PARTITIONS) --replication-factor $(REPLICATION)

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

# -------- Spark consumer (Kafka -> Delta on local disk) --------
.PHONY: stream
stream:
	$(SPARK_SUBMIT) \
	  --master $(SPARK_MASTER) \
	  --packages $(SPARK_PACKAGES) \
	  --conf spark.sql.extensions=io
