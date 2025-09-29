#!/usr/bin/env python3
"""
Gold Layer Consumer Base Class

Implements gold-specific behavior for the medallion architecture.
Gold layer focuses on business-level aggregations and KPIs.
"""

from abc import abstractmethod
from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from .base_stream_consumer import BaseStreamConsumer


class GoldConsumer(BaseStreamConsumer):
    """
    Gold layer consumer base class.

    Gold layer responsibilities:
    - Business-level aggregations
    - KPI calculations
    - Dimensional modeling
    - Ready-to-serve data for analytics
    """

    def __init__(self, *args, aggregation_window: str = "1 hour", watermark: str = "10 minutes", **kwargs):
        """
        Initialize gold consumer.

        Args:
            aggregation_window: Window duration for aggregations
            watermark: Watermark for handling late data
            *args, **kwargs: Passed to parent class
        """
        super().__init__(*args, **kwargs)
        self.aggregation_window = aggregation_window
        self.watermark = watermark

    def add_metadata(self, df: DataFrame) -> DataFrame:
        """
        Add gold layer metadata.

        Adds:
        - Gold processing timestamp
        - Aggregation window info
        - Consumer metadata

        Args:
            df: Input DataFrame

        Returns:
            DataFrame with gold metadata added
        """
        return (
            df
            .withColumn("gold_processing_time", F.current_timestamp())
            .withColumn("gold_consumer_id", F.lit(self.consumer_id))
            .withColumn("gold_consumer_version", F.lit(self.consumer_version))
            .withColumn("aggregation_window", F.lit(self.aggregation_window))
        )

    def validate(self, df: DataFrame) -> DataFrame:
        """
        Gold layer validation.

        Applies business rule validation.

        Args:
            df: Input DataFrame

        Returns:
            DataFrame with business validation applied
        """
        # Default implementation - can be overridden
        return df

    @abstractmethod
    def aggregate(self, df: DataFrame) -> DataFrame:
        """
        Perform business aggregations.

        Must be implemented by specific consumers.

        Args:
            df: Input DataFrame

        Returns:
            Aggregated DataFrame
        """
        pass

    def transform(self, df: DataFrame) -> DataFrame:
        """
        Gold layer transformation.

        Applies aggregations and business logic.

        Args:
            df: Input DataFrame

        Returns:
            Transformed DataFrame with aggregations
        """
        # Apply watermark for stateful aggregations
        if "event_timestamp" in df.columns:
            df = df.withWatermark("event_timestamp", self.watermark)

        # Perform aggregations (implemented by subclasses)
        df = self.aggregate(df)

        return df

    def calculate_kpis(self, df: DataFrame) -> DataFrame:
        """
        Calculate business KPIs.

        Can be overridden by specific consumers.

        Args:
            df: Aggregated DataFrame

        Returns:
            DataFrame with KPIs added
        """
        return df

    def add_dimensions(self, df: DataFrame, dimension_df: DataFrame, join_keys: list) -> DataFrame:
        """
        Join with dimension tables.

        Args:
            df: Fact DataFrame
            dimension_df: Dimension DataFrame
            join_keys: Columns to join on

        Returns:
            DataFrame with dimensions added
        """
        return df.join(dimension_df, on=join_keys, how="left")

    def get_output_mode(self) -> str:
        """
        Gold typically uses complete or update mode for aggregations.

        Returns:
            "update" - for incremental aggregations
        """
        return "update"

    def get_partitioning_columns(self) -> list:
        """
        Gold partitions by aggregation window.

        Returns:
            List of partitioning columns
        """
        return ["window_year", "window_month", "window_day"]

    def add_window_partitions(self, df: DataFrame) -> DataFrame:
        """
        Add partitioning columns based on aggregation window.

        Args:
            df: Aggregated DataFrame with window column

        Returns:
            DataFrame with partitioning columns added
        """
        if "window" in df.columns:
            df = (
                df
                .withColumn("window_start", F.col("window.start"))
                .withColumn("window_end", F.col("window.end"))
                .withColumn("window_year", F.year("window_start"))
                .withColumn("window_month", F.month("window_start"))
                .withColumn("window_day", F.dayofmonth("window_start"))
                .withColumn("window_hour", F.hour("window_start"))
            )
        return df

    def write_output(self, df: DataFrame):
        """
        Write to Iceberg table with gold-specific settings.

        Args:
            df: Processed DataFrame to write

        Returns:
            StreamingQuery object
        """
        # Configure Iceberg catalog
        catalog_name = self.target_table.split('.')[0] if '.' in self.target_table else "gold"

        # Ensure database exists
        self.spark.sql(f"CREATE DATABASE IF NOT EXISTS {catalog_name}")

        # Add window partitions if needed
        if "window" in df.columns:
            df = self.add_window_partitions(df)

        # Configure Iceberg write with gold-specific optimizations
        return (
            df.writeStream
            .outputMode(self.get_output_mode())
            .format("iceberg")
            .option("path", f"s3a://lakehouse/iceberg/{self.target_table.replace('.', '/')}")
            .option("checkpointLocation", self.checkpoint_location)
            .option("fanout-enabled", "false")  # Controlled writes for aggregations
            .trigger(processingTime=self.trigger_interval)
            .partitionBy(*self.get_partitioning_columns())
            .toTable(self.target_table)
        )