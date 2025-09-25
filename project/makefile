# =========================
# CityScale Makefile
# =========================
# Place this file at: cityscale/Makefile
# Requires: docker, docker compose
# Optional: local spark-submit in PATH (for the `stream` target)

# -------- Settings (override via env or CLI: make VAR=val) --------
COMPOSE        ?= docker compose
PYTHON         ?= python
PIP            ?= pip

# Kafka / Topic
KAFKA_BROKER   ?= localhost:9092
TOPIC          ?= trips.yellow
PARTITIONS     ?= 12
REPLICATION    ?= 1

# Producer scope & behavior
DATASET        ?= yellow           # yellow|green|fhv|fhvhv
YEARS          ?= 2024             # e.g., "2024" or "2023,2024"
MONTHS         ?= 06,07,08         # blank => all months
RATE           ?= 2000             # messages/sec
BATCH          ?= 2000
SALT_KEYS      ?= 8                # 0 => no salting
KEY_MODE       ?= pu               # pu|how_pu
P_LATE         ?= 0.00             # fraction 0..1
LATE_MIN       ?= 300              # seconds
LATE_MAX       ?= 1200             # seconds

# MinIO (S3-compatible) sink
MINIO_HOST     ?= localhost:9000
MINIO_ACCESS   ?= admin
MINIO_SECRET   ?= admin12345
S3_BUCKET      ?= cityscale
S3A_ENDPOINT   ?= http://minio:9000    # inside containers, service name is "minio"

# Spark & Delta
SPARK_SUBMIT   ?= spark-submit
SPARK_PACKAGES ?= org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1,io.delta:delta-spark_2.12:3.2.0
DELTA_VER      ?= 3.2.0

# Python venv dir
VENV_DIR       ?= .venv

# -----------------------------------------------------------------
# Help
# -----------------------------------------------------------------
.PHONY: help
help:
	@echo ""
	@echo "CityScale Makefile — common tasks"
	@echo "------------------------------------------------------------"
	@echo "make up                 # start Kafka, Kafka-UI, MinIO"
	@echo "make down               # stop all containers"
	@echo "make logs               # tail logs"
	@echo "make create-topic       # create Kafka topic ($(TOPIC))"
	@echo "make create-bucket      # create MinIO bucket ($(S3_BUCKET))"
	@echo "make venv install       # create venv & install replayer deps"
	@echo "make replay             # run DuckDB -> Kafka producer"
	@echo "make stream             # run Spark Structured Streaming job (local spark-submit)"
	@echo "make stream-dry         # print spark-submit command only"
	@echo "make clean              # remove venv and Python caches"
	@echo ""
	@echo "Override defaults: make TOPIC=trips.yellow YEARS=2024 MONTHS=01,02 RATE=5000 replay"
	@echo ""

# -----------------------------------------------------------------
# Stack lifecycle
# -----------------------------------------------------------------
.PHONY: up down logs reset
up:
	$(COMPOSE) up -d

down:
	$(COMPOSE) down

logs:
	$(COMPOSE) logs -f --tail=200

reset: down up

# -----------------------------------------------------------------
# Infra: topic & bucket
# -----------------------------------------------------------------
.PHONY: create-topic create-bucket
create-topic:
	docker run --rm --network host confluentinc/cp-kafka:7.6.1 \
	  kafka-topics --bootstrap-server $(KAFKA_BROKER) \
	  --create --if-not-exists --topic $(TOPIC) \
	  --partitions $(PARTITIONS) --replication-factor $(REPLICATION)

create-bucket:
	docker run --rm --network host -e MC_HOST_local=http://$(MINIO_ACCESS):$(MINIO_SECRET)@$(MINIO_HOST) \
	  minio/mc:latest mb -p local/$(S3_BUCKET) || true

# -----------------------------------------------------------------
# Python environment for producer
# -----------------------------------------------------------------
.PHONY: venv install
venv:
	$(PYTHON) -m venv $(VENV_DIR)
	@echo "Run: source $(VENV_DIR)/bin/activate"

install: venv
	. $(VENV_DIR)/bin/activate && \
	$(PIP) install --upgrade pip && \
	$(PIP) install -r replayer/requirements.txt

# -----------------------------------------------------------------
# Producer (DuckDB -> Kafka)
# -----------------------------------------------------------------
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

# -----------------------------------------------------------------
# Streaming job (Spark → Delta on MinIO)
# Assumes you have streaming/spark_job.py implemented to read from Kafka and write Delta
# -----------------------------------------------------------------
.PHONY: stream stream-dry
stream:
	$(SPARK_SUBMIT) \
	  --packages $(SPARK_PACKAGES) \
	  --conf spark.sql.extensions=io.delta.sql.DeltaSparkSessionExtension \
	  --conf spark.sql.catalog.spark_catalog=org.apache.spark.sql.delta.catalog.DeltaCatalog \
	  --conf spark.hadoop.fs.s3a.endpoint=$(S3A_ENDPOINT) \
	  --conf spark.hadoop.fs.s3a.access.key=$(MINIO_ACCESS) \
	  --conf spark.hadoop.fs.s3a.secret.key=$(MINIO_SECRET) \
	  --conf spark.hadoop.fs.s3a.path.style.access=true \
	  --conf spark.sql.shuffle.partitions=200 \
	  streaming/spark_job.py \
	  --kafka.bootstrap $(KAFKA_BROKER) \
	  --topic $(TOPIC) \
	  --checkpoint s3a://$(S3_BUCKET)/chk/pu_hour/ \
	  --output s3a://$(S3_BUCKET)/delta/pu_hour/

stream-dry:
	@echo "$(SPARK_SUBMIT) --packages $(SPARK_PACKAGES) ..." && \
	echo "  --conf spark.hadoop.fs.s3a.endpoint=$(S3A_ENDPOINT)" && \
	echo "  --conf spark.hadoop.fs.s3a.access.key=$(MINIO_ACCESS)" && \
	echo "  --conf spark.hadoop.fs.s3a.secret.key=$(MINIO_SECRET)" && \
	echo "  streaming/spark_job.py --kafka.bootstrap $(KAFKA_BROKER) --topic $(TOPIC) --checkpoint s3a://$(S3_BUCKET)/chk/pu_hour/ --output s3a://$(S3_BUCKET)/delta/pu_hour/"

# -----------------------------------------------------------------
# Utilities
# -----------------------------------------------------------------
.PHONY: clean
clean:
	rm -rf $(VENV_DIR) **/__pycache__ **/*.pyc .pytest_cache
