#!/usr/bin/env python3
"""
Base Producer Class for Kafka Producers

Provides common functionality for all Kafka producers to reduce code duplication.
Handles schema loading, Kafka setup, metrics tracking, and signal handling.
"""

import os
import sys
import time
import signal
import socket
import uuid
from datetime import datetime
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Dict, Any, Tuple, Generator, Optional

from confluent_kafka import Producer
from confluent_kafka.serialization import SerializationContext, MessageField
from confluent_kafka.schema_registry import SchemaRegistryClient
from confluent_kafka.schema_registry.avro import AvroSerializer


class BaseProducer(ABC):
    """Base class for all Kafka producers with common functionality."""

    def __init__(
        self,
        name: str,
        schema_file: str,
        topic: str = None,
        bootstrap_servers: str = None,
        schema_registry_url: str = None,
        rate: float = None,
        batch_size: int = None
    ):
        """
        Initialize base producer.

        Args:
            name: Producer name for logging (e.g., "trips", "weather")
            schema_file: Name of Avro schema file in schemas directory
            topic: Kafka topic (can be overridden by env var)
            bootstrap_servers: Kafka bootstrap servers (can be overridden by env var)
            schema_registry_url: Schema Registry URL (can be overridden by env var)
            rate: Messages per second target (can be overridden by env var)
            batch_size: Batch size for sending (can be overridden by env var)
        """
        self.name = name
        self.schema_file = schema_file
        self.topic = topic or os.getenv("TOPIC", f"{name}.default")
        self.bootstrap_servers = bootstrap_servers or os.getenv("KAFKA_BOOTSTRAP", "localhost:29092")
        self.schema_registry_url = schema_registry_url or os.getenv("SCHEMA_REGISTRY_URL", "http://localhost:8082")
        self.rate = rate if rate is not None else float(os.getenv("RATE", "1000"))
        self.batch_size = batch_size if batch_size is not None else int(os.getenv("BATCH", "1000"))

        # Metrics tracking
        self.stats = {"sent": 0, "delivered": 0, "failed": 0, "start": time.time()}
        self.stop_flag = False

        # Kafka components (initialized in setup)
        self.producer = None
        self.avro_serializer = None

        # Metadata
        self.producer_id = str(uuid.uuid4())
        self.hostname = socket.gethostname()
        self.producer_version = "1.0.0"  # Can be overridden or fetched from git

    def load_schema(self) -> str:
        """Load Avro schema from file."""
        # Go up from producers/core/base.py to project root, then to schemas
        schema_path = Path(__file__).parent.parent.parent.parent / "schemas" / self.schema_file

        if schema_path.exists():
            with open(schema_path, 'r') as f:
                return f.read()
        else:
            print(f"✗ Schema file not found: {schema_path}", file=sys.stderr)
            print(f"  Please ensure schemas/{self.schema_file} exists", file=sys.stderr)
            sys.exit(1)

    def create_producer(self, config_overrides: Dict[str, Any] = None) -> Producer:
        """
        Create Kafka producer with standard configuration.

        Args:
            config_overrides: Additional configuration to override defaults

        Returns:
            Configured Kafka Producer instance
        """
        conf = {
            "bootstrap.servers": self.bootstrap_servers,
            "acks": "all",
            "compression.type": "snappy",
            "linger.ms": 10,
            "batch.num.messages": max(self.batch_size, 1000),
        }

        if config_overrides:
            conf.update(config_overrides)

        return Producer(conf)

    def setup_avro_serialization(self) -> AvroSerializer:
        """Setup Schema Registry and Avro serializer."""
        schema_str = self.load_schema()

        try:
            schema_registry_conf = {'url': self.schema_registry_url}
            schema_registry_client = SchemaRegistryClient(schema_registry_conf)

            avro_serializer = AvroSerializer(
                schema_registry_client,
                schema_str,
                conf={'auto.register.schemas': True}
            )
            print(f"✓ Connected to Schema Registry at {self.schema_registry_url}", file=sys.stderr)
            return avro_serializer

        except Exception as e:
            print(f"✗ Failed to connect to Schema Registry: {e}", file=sys.stderr)
            print(f"  Please ensure Schema Registry is running at {self.schema_registry_url}", file=sys.stderr)
            print("  Run: make up", file=sys.stderr)
            sys.exit(1)

    def setup_signal_handlers(self):
        """Setup graceful shutdown handlers."""
        def handle_sig(*_):
            self.stop_flag = True
            print(f"\n[{self.name}] Stopping... flushing...", file=sys.stderr)

        signal.signal(signal.SIGINT, handle_sig)
        signal.signal(signal.SIGTERM, handle_sig)

    def delivery_callback(self, err, msg):
        """Standard delivery callback for tracking metrics."""
        if err:
            self.stats["failed"] += 1
        else:
            self.stats["delivered"] += 1

    def send_record(self, key: bytes, value: bytes, headers: Dict[str, str] = None):
        """
        Send a single record to Kafka with headers.

        Args:
            key: Record key (already encoded)
            value: Record value (already serialized)
            headers: Optional additional headers
        """
        # Build default headers
        default_headers = {
            'producer-id': self.producer_id,
            'producer-name': self.name,
            'producer-version': self.producer_version,
            'processing-time': datetime.now().isoformat(),
            'hostname': self.hostname
        }

        # Merge with any provided headers
        if headers:
            default_headers.update(headers)

        # Convert headers to list of tuples for Kafka
        kafka_headers = [(k, v.encode('utf-8') if v else b'') for k, v in default_headers.items()]

        try:
            self.producer.produce(
                self.topic,
                key=key,
                value=value,
                headers=kafka_headers,
                callback=self.delivery_callback
            )
            self.stats["sent"] += 1
            # Poll to trigger any available callbacks
            self.producer.poll(0)
        except BufferError:
            # Internal queue is full, need to wait for messages to be delivered
            print(f"[WARNING] Producer buffer full, flushing...", file=sys.stderr)
            self.producer.flush(timeout=5)
            # Retry after flush
            self.producer.produce(
                self.topic,
                key=key,
                value=value,
                headers=kafka_headers,
                callback=self.delivery_callback
            )
            self.stats["sent"] += 1
            self.producer.poll(0)

    def send_batch(self, records: list):
        """
        Send a batch of records to Kafka.

        Args:
            records: List of (key, value) or (key, value, headers) tuples
        """
        for record in records:
            if len(record) == 3:
                key, value, headers = record
                self.send_record(key, value, headers)
            else:
                key, value = record
                self.send_record(key, value)

        # Flush with timeout and check for unsent messages
        remaining = self.producer.flush(timeout=10)
        if remaining > 0:
            print(f"[WARNING] {remaining} messages still in queue after flush", file=sys.stderr)

        # Process any remaining callbacks
        self.producer.poll(0)

    def print_metrics(self, additional_info: str = ""):
        """Print standardized metrics."""
        dur = time.time() - self.stats["start"]
        rate = self.stats["sent"] / max(dur, 1e-6)
        base_msg = f"[{self.name}] sent={self.stats['sent']} "

        # Add delivery stats if available
        if self.stats["delivered"] > 0 or self.stats["failed"] > 0:
            base_msg += f"delivered={self.stats['delivered']} failed={self.stats['failed']} "

        base_msg += f"rate={rate:.0f} msg/s"

        if additional_info:
            base_msg += f" {additional_info}"

        print(base_msg, file=sys.stderr)

    def print_summary(self, additional_info: str = ""):
        """Print final summary."""
        dur = time.time() - self.stats["start"]
        rate = self.stats["sent"] / max(dur, 1e-6)

        summary = f"\n[{self.name}] DONE in {dur:.1f}s  sent={self.stats['sent']}  "
        summary += f"delivered={self.stats['delivered']} failed={self.stats['failed']}  "
        summary += f"avg_rate={rate:.0f} msg/s"

        if additional_info:
            summary += f"  {additional_info}"

        print(summary, file=sys.stderr)

    def rate_limit(self, batch_size: int, batch_start_time: float):
        """
        Apply rate limiting based on configured rate.

        Args:
            batch_size: Number of messages in the batch
            batch_start_time: Time when batch started
        """
        if self.rate <= 0:
            return

        elapsed = time.time() - batch_start_time
        target_time = batch_size / self.rate
        sleep_time = max(0.0, target_time - elapsed)

        if sleep_time > 0:
            time.sleep(sleep_time)

    def setup(self):
        """Setup producer components - called once before run()."""
        print(f"Starting {self.name.title()} Producer", file=sys.stderr)
        print(f"Topic: {self.topic}", file=sys.stderr)
        print(f"Rate: {self.rate} msg/s, Batch: {self.batch_size}", file=sys.stderr)

        self.setup_signal_handlers()
        self.producer = self.create_producer(self.get_producer_config())
        self.avro_serializer = self.setup_avro_serialization()

    def cleanup(self):
        """Cleanup producer resources."""
        if self.producer:
            remaining = self.producer.flush(timeout=30)
            if remaining > 0:
                print(f"[WARNING] {remaining} messages were not sent during cleanup", file=sys.stderr)
        self.print_summary(self.get_summary_info())

    def get_data_source_info(self) -> Tuple[str, bool]:
        """
        Determine data source type and whether it's synthetic.

        Returns:
            Tuple of (data_source_name, is_synthetic)
        """
        if not hasattr(self, 'data_source'):
            return ("UNKNOWN", False)

        if hasattr(self.data_source, '__class__'):
            source_name = self.data_source.__class__.__name__
            if 'Synthetic' in source_name:
                return ("SYNTHETIC", True)
            elif 'NYCOpenData' in source_name:
                return ("NYC_OPEN_DATA", False)
            elif 'NYCTLC' in source_name:
                return ("NYC_TLC", False)
            elif 'NOAA' in source_name:
                return ("NOAA", False)
            elif 'Mixed' in source_name:
                return ("MIXED", False)

        return ("UNKNOWN", False)

    def get_standard_metadata(self, record: Any, additional_metadata: Dict[str, Any] = None) -> Dict[str, Any]:
        """
        Generate standard metadata fields for a record.

        Args:
            record: The record being processed
            additional_metadata: Additional metadata to merge

        Returns:
            Dictionary with standard metadata fields
        """
        data_source, is_synthetic = self.get_data_source_info()

        metadata = {
            "producer_id": self.producer_id,
            "data_source": data_source,
            "processing_time": datetime.now().isoformat(),
            "is_synthetic": is_synthetic
        }

        if additional_metadata:
            metadata.update(additional_metadata)

        return metadata

    def get_standard_headers(self, record: Any, additional_headers: Dict[str, str] = None) -> Dict[str, str]:
        """
        Generate standard headers for a record.

        Args:
            record: The record being processed
            additional_headers: Additional headers to merge

        Returns:
            Dictionary with standard headers
        """
        data_source, is_synthetic = self.get_data_source_info()

        headers = {
            'data-source': data_source,
            'synthetic': str(is_synthetic).lower()
        }

        if additional_headers:
            headers.update(additional_headers)

        return headers

    def process_record_common(self,
                             record: Any,
                             key: str,
                             payload: Dict[str, Any],
                             additional_headers: Dict[str, str] = None) -> Tuple[bytes, bytes, Dict[str, str]]:
        """
        Common record processing logic used by all producers.

        Args:
            record: The original record
            key: The key for this record
            payload: The payload to serialize (should include all fields)
            additional_headers: Producer-specific headers

        Returns:
            Tuple of (key_bytes, value_bytes, headers)
        """
        # Add standard metadata to payload
        metadata = self.get_standard_metadata(record)
        payload_with_metadata = {**payload, **metadata}

        # Serialize
        key_bytes = key.encode("utf-8")
        value_bytes = self.avro_serializer(
            payload_with_metadata,
            SerializationContext(self.topic, MessageField.VALUE)
        )

        # Generate headers
        headers = self.get_standard_headers(record, additional_headers)

        return key_bytes, value_bytes, headers

    # Abstract methods that subclasses must implement

    @abstractmethod
    def get_producer_config(self) -> Dict[str, Any]:
        """
        Get producer-specific configuration overrides.

        Returns:
            Dictionary of configuration overrides
        """
        pass

    @abstractmethod
    def fetch_data(self) -> Generator:
        """
        Fetch or generate data to be produced.

        Returns:
            Generator yielding data records
        """
        pass

    @abstractmethod
    def process_record(self, record: Any) -> Tuple[bytes, bytes, Optional[Dict[str, str]]]:
        """
        Process a single record into key, value, and optional headers.

        Args:
            record: Raw data record

        Returns:
            Tuple of (key_bytes, value_bytes, optional_headers) ready to send
        """
        pass

    def get_summary_info(self) -> str:
        """
        Get additional info for summary. Override for custom summary.

        Returns:
            Additional summary information
        """
        return ""

    def run(self):
        """
        Main producer loop.
        Can be overridden for custom behavior, but usually not necessary.
        """
        self.setup()

        # Fetch data
        data_generator = self.fetch_data()
        if not data_generator:
            print(f"[{self.name}] No data to process", file=sys.stderr)
            self.cleanup()
            return

        # Process loop
        last_print = time.time()
        last_flush = time.time()
        batch = []
        batch_start = time.time()
        messages_in_batch = 0

        for record in data_generator:
            if self.stop_flag:
                break

            # Process and add to batch
            result = self.process_record(record)
            if len(result) == 3:
                key, value, headers = result
                batch.append((key, value, headers))
            else:
                key, value = result
                batch.append((key, value))

            messages_in_batch += 1

            # Debug output for first few messages
            if self.stats["sent"] + messages_in_batch <= 5:
                print(f"[DEBUG] Added message {self.stats['sent'] + messages_in_batch} to batch (batch size: {len(batch)})", file=sys.stderr)

            # Check for periodic flush FIRST (before batch full check)
            now = time.time()
            if now - last_flush >= 2.0 and batch:
                print(f"[DEBUG] Periodic flush: sending {len(batch)} messages", file=sys.stderr)
                self.send_batch(batch)
                self.rate_limit(len(batch), batch_start)
                batch = []
                messages_in_batch = 0
                batch_start = time.time()
                last_flush = now
            # Send batch when full
            elif len(batch) >= self.batch_size:
                print(f"[DEBUG] Sending full batch of {len(batch)} messages", file=sys.stderr)
                self.send_batch(batch)
                self.rate_limit(len(batch), batch_start)
                batch = []
                messages_in_batch = 0
                batch_start = time.time()
                last_flush = time.time()

            # Periodic metrics
            if now - last_print >= 10.0:
                self.print_metrics()
                last_print = now

        # Send remaining batch
        if batch:
            print(f"[DEBUG] Final flush: sending {len(batch)} messages", file=sys.stderr)
            self.send_batch(batch)

        self.cleanup()


# Backwards compatibility alias
ConfigurableProducer = BaseProducer