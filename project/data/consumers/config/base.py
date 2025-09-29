#!/usr/bin/env python3
"""
Base Configuration for Bronze Layer Consumers

Provides common configuration structure for all bronze layer consumers
following the medallion architecture pattern.
"""

from dataclasses import dataclass
from typing import Dict, Optional


@dataclass
class BaseConsumerConfig:
    """Base configuration for all bronze layer consumers."""

    # Kafka Configuration
    kafka_bootstrap: str = "kafka:9092"
    schema_registry_url: str = "http://schema-registry:8081"

    # Source Configuration
    source_topic: str = ""
    schema_subject: str = ""

    # Target Configuration
    target_table: str = ""
    iceberg_catalog: str = "bronze"
    warehouse_path: str = "s3a://lakehouse/iceberg"

    # Streaming Configuration
    trigger_interval: str = "30 seconds"
    max_offsets_per_trigger: int = 10000
    starting_offsets: str = "latest"
    checkpoint_location: str = ""

    # S3/MinIO Configuration
    s3_endpoint: str = "http://minio:9000"
    s3_access_key: str = "admin"
    s3_secret_key: str = "admin123"
    s3_ssl_enabled: bool = False

    # Consumer Metadata
    consumer_version: str = "1.0.0"
    consumer_name: str = ""

    # Processing Configuration
    enable_metrics: bool = True
    dead_letter_table: Optional[str] = None

    # Performance Tuning
    executor_instances: int = 2
    executor_cores: int = 2
    executor_memory: str = "2g"

    def to_spark_conf(self) -> Dict[str, str]:
        """
        Convert configuration to Spark configuration dictionary.

        Returns:
            Dictionary of Spark configuration parameters
        """
        return {
            # Iceberg catalog
            f"spark.sql.catalog.{self.iceberg_catalog}": "org.apache.iceberg.spark.SparkCatalog",
            f"spark.sql.catalog.{self.iceberg_catalog}.type": "hadoop",
            f"spark.sql.catalog.{self.iceberg_catalog}.warehouse": self.warehouse_path,

            # S3/MinIO
            "spark.hadoop.fs.s3a.endpoint": self.s3_endpoint,
            "spark.hadoop.fs.s3a.path.style.access": "true",
            "spark.hadoop.fs.s3a.access.key": self.s3_access_key,
            "spark.hadoop.fs.s3a.secret.key": self.s3_secret_key,
            "spark.hadoop.fs.s3a.impl": "org.apache.hadoop.fs.s3a.S3AFileSystem",
            "spark.hadoop.fs.s3a.connection.ssl.enabled": str(self.s3_ssl_enabled).lower(),

            # Performance
            "spark.executor.instances": str(self.executor_instances),
            "spark.executor.cores": str(self.executor_cores),
            "spark.executor.memory": self.executor_memory,

            # Streaming
            "spark.sql.adaptive.enabled": "false",
            "spark.sql.streaming.stateStore.stateSchemaCheck": "false",
            "spark.sql.session.timeZone": "UTC",
        }

    def get_checkpoint_location(self) -> str:
        """
        Get checkpoint location, auto-generating if not specified.

        Returns:
            Checkpoint location path
        """
        if self.checkpoint_location:
            return self.checkpoint_location

        # Auto-generate based on consumer name and table
        if self.consumer_name:
            return f"/tmp/checkpoint/bronze/{self.consumer_name}"
        elif self.target_table:
            table_name = self.target_table.split('.')[-1]
            return f"/tmp/checkpoint/bronze/{table_name}"
        else:
            return "/tmp/checkpoint/bronze/default"

    def get_schema_subject(self) -> str:
        """
        Get schema subject, auto-generating if not specified.

        Returns:
            Schema Registry subject name
        """
        if self.schema_subject:
            return self.schema_subject

        # Auto-generate from topic name
        if self.source_topic:
            return f"{self.source_topic}-value"

        return ""

    def validate(self) -> bool:
        """
        Validate configuration.

        Returns:
            True if configuration is valid

        Raises:
            ValueError: If configuration is invalid
        """
        errors = []

        # Required fields
        if not self.source_topic:
            errors.append("source_topic is required")
        if not self.target_table:
            errors.append("target_table is required")
        if not self.kafka_bootstrap:
            errors.append("kafka_bootstrap is required")
        if not self.schema_registry_url:
            errors.append("schema_registry_url is required")

        # Value constraints
        if self.max_offsets_per_trigger <= 0:
            errors.append("max_offsets_per_trigger must be positive")
        if self.starting_offsets not in ["earliest", "latest"]:
            errors.append("starting_offsets must be 'earliest' or 'latest'")

        if errors:
            raise ValueError(f"Configuration validation failed: {'; '.join(errors)}")

        return True

    def __post_init__(self):
        """Post-initialization processing."""
        # Auto-generate dependent fields
        if not self.checkpoint_location:
            self.checkpoint_location = self.get_checkpoint_location()

        if not self.schema_subject:
            self.schema_subject = self.get_schema_subject()

    def summary(self) -> str:
        """
        Get configuration summary for logging.

        Returns:
            Human-readable configuration summary
        """
        return f"""
Consumer Configuration:
  Name: {self.consumer_name}
  Source: {self.source_topic} -> {self.target_table}
  Kafka: {self.kafka_bootstrap}
  Schema Registry: {self.schema_registry_url}
  Trigger: {self.trigger_interval}
  Batch Size: {self.max_offsets_per_trigger}
  Starting: {self.starting_offsets}
  Checkpoint: {self.checkpoint_location}
  Metrics: {self.enable_metrics}
"""