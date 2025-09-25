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
	@echo "  up              - start Kafka services"
	@echo "  down            - stop services and remove volumes"
	@echo "  logs            - tail compose logs"
	@echo "  kafka-topics    - list all Kafka topics"
	@echo "  kafka-describe  - describe topic details (default: trips.yellow)"
	@echo "  kafka-consume   - sample 10 messages from topic"
	@echo "  kafka-offsets   - show partition offsets for topic"
	@echo "  kafka-delete-topic - delete a topic"
	@echo "  install         - create venv and install producer dependencies"
	@echo "  replay          - run DuckDB->Kafka producer (from host)"
	@echo "  clean           - remove venv and caches"
	@echo ""
	@echo "Override: make TOPIC=trips.yellow YEARS=2024 MONTHS=01,02 RATE=5000 replay"

# -------- Compose lifecycle --------
.PHONY: up down logs
up:
	$(COMPOSE) up -d --wait

down:
	$(COMPOSE) down -v

logs:
	$(COMPOSE) logs -f --tail=200

# -------- Kafka utilities --------
.PHONY: kafka-topics kafka-describe kafka-consume kafka-offsets kafka-delete-topic

kafka-topics:
	docker exec kafka /opt/bitnami/kafka/bin/kafka-topics.sh \
		--bootstrap-server localhost:9092 --list

kafka-describe:
	docker exec kafka /opt/bitnami/kafka/bin/kafka-topics.sh \
		--bootstrap-server localhost:9092 \
		--describe --topic $(TOPIC)

kafka-consume:
	docker exec kafka /opt/bitnami/kafka/bin/kafka-console-consumer.sh \
		--bootstrap-server localhost:9092 \
		--topic $(TOPIC) \
		--from-beginning \
		--max-messages 10

kafka-offsets:
	docker exec kafka /opt/bitnami/kafka/bin/kafka-run-class.sh \
		kafka.tools.GetOffsetShell \
		--broker-list localhost:9092 \
		--topic $(TOPIC)

kafka-delete-topic:
	docker exec kafka /opt/bitnami/kafka/bin/kafka-topics.sh \
		--bootstrap-server localhost:9092 \
		--delete --topic $(TOPIC)

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

# -------- Clean up --------
.PHONY: clean
clean:
	rm -rf $(VENV_DIR)
	find . -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true
	find . -type f -name "*.pyc" -delete 2>/dev/null || true
