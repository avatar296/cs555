#!/usr/bin/env python3
"""
Quality Check Mixins for Medallion Architecture

Provides reusable quality validation logic that can be mixed into
silver and gold layer consumers following DRY principles.
"""

from pyspark.sql import DataFrame
from pyspark.sql import functions as F


class TripQualityMixin:
    """
    Quality checks for trip data.

    Can be mixed into silver/gold trip consumers.
    """

    def validate_trip_distance(self, df: DataFrame) -> DataFrame:
        """
        Validate trip distance is within reasonable bounds.

        Args:
            df: DataFrame with 'dist' column

        Returns:
            DataFrame with 'valid_distance' boolean column
        """
        return df.withColumn(
            "valid_distance",
            (F.col("dist") >= 0.0) & (F.col("dist") <= 1000.0)
        )

    def validate_trip_locations(self, df: DataFrame) -> DataFrame:
        """
        Validate pickup and dropoff locations.

        Args:
            df: DataFrame with 'pu' and 'do' columns

        Returns:
            DataFrame with 'valid_locations' boolean column
        """
        # TLC zone IDs range from 1 to 265
        return df.withColumn(
            "valid_locations",
            (F.col("pu").isNotNull()) &
            (F.col("do").isNotNull()) &
            (F.col("pu") > 0) &
            (F.col("pu") <= 265) &
            (F.col("do") > 0) &
            (F.col("do") <= 265)
        )

    def validate_trip_timestamp(self, df: DataFrame, timestamp_col: str = "ts") -> DataFrame:
        """
        Validate trip timestamp is reasonable.

        Args:
            df: DataFrame with timestamp column
            timestamp_col: Name of timestamp column

        Returns:
            DataFrame with 'valid_timestamp' boolean column
        """
        # Trips should be within last 10 years and not in future
        min_date = F.date_sub(F.current_date(), 3650)  # ~10 years ago
        max_date = F.date_add(F.current_date(), 1)     # Allow 1 day future for timezone

        return df.withColumn(
            "valid_timestamp",
            (F.col(timestamp_col).isNotNull()) &
            (F.to_date(timestamp_col) >= min_date) &
            (F.to_date(timestamp_col) <= max_date)
        )

    def calculate_trip_quality_score(self, df: DataFrame) -> DataFrame:
        """
        Calculate overall quality score for trip records.

        Args:
            df: DataFrame with validation columns

        Returns:
            DataFrame with 'trip_quality_score' column (0.0 to 1.0)
        """
        # First apply all validations
        df = self.validate_trip_distance(df)
        df = self.validate_trip_locations(df)
        df = self.validate_trip_timestamp(df)

        # Calculate weighted quality score
        return df.withColumn(
            "trip_quality_score",
            (
                F.when(F.col("valid_distance"), 0.4).otherwise(0.0) +
                F.when(F.col("valid_locations"), 0.4).otherwise(0.0) +
                F.when(F.col("valid_timestamp"), 0.2).otherwise(0.0)
            )
        )


class WeatherQualityMixin:
    """
    Quality checks for weather data.

    Can be mixed into silver/gold weather consumers.
    """

    def validate_temperature(self, df: DataFrame, temp_col: str = "temp_f") -> DataFrame:
        """
        Validate temperature is within reasonable bounds.

        Args:
            df: DataFrame with temperature column
            temp_col: Name of temperature column (Fahrenheit)

        Returns:
            DataFrame with 'valid_temperature' boolean column
        """
        # Reasonable temperature range in Fahrenheit
        return df.withColumn(
            "valid_temperature",
            (F.col(temp_col).isNotNull()) &
            (F.col(temp_col) >= -50.0) &
            (F.col(temp_col) <= 150.0)
        )

    def validate_humidity(self, df: DataFrame, humidity_col: str = "humidity") -> DataFrame:
        """
        Validate humidity percentage.

        Args:
            df: DataFrame with humidity column
            humidity_col: Name of humidity column

        Returns:
            DataFrame with 'valid_humidity' boolean column
        """
        return df.withColumn(
            "valid_humidity",
            (F.col(humidity_col).isNotNull()) &
            (F.col(humidity_col) >= 0.0) &
            (F.col(humidity_col) <= 100.0)
        )

    def validate_wind_speed(self, df: DataFrame, wind_col: str = "wind_speed") -> DataFrame:
        """
        Validate wind speed.

        Args:
            df: DataFrame with wind speed column
            wind_col: Name of wind speed column (mph)

        Returns:
            DataFrame with 'valid_wind' boolean column
        """
        # Max recorded wind speed is ~253 mph
        return df.withColumn(
            "valid_wind",
            (F.col(wind_col).isNotNull()) &
            (F.col(wind_col) >= 0.0) &
            (F.col(wind_col) <= 300.0)
        )

    def validate_pressure(self, df: DataFrame, pressure_col: str = "pressure") -> DataFrame:
        """
        Validate atmospheric pressure.

        Args:
            df: DataFrame with pressure column
            pressure_col: Name of pressure column (millibars)

        Returns:
            DataFrame with 'valid_pressure' boolean column
        """
        # Typical pressure range in millibars
        return df.withColumn(
            "valid_pressure",
            (F.col(pressure_col).isNotNull()) &
            (F.col(pressure_col) >= 800.0) &
            (F.col(pressure_col) <= 1100.0)
        )

    def calculate_weather_quality_score(self, df: DataFrame) -> DataFrame:
        """
        Calculate overall quality score for weather records.

        Args:
            df: DataFrame with weather data

        Returns:
            DataFrame with 'weather_quality_score' column (0.0 to 1.0)
        """
        # Apply validations
        df = self.validate_temperature(df)
        df = self.validate_humidity(df)
        df = self.validate_wind_speed(df)
        df = self.validate_pressure(df)

        # Calculate weighted score (temperature is most important)
        return df.withColumn(
            "weather_quality_score",
            (
                F.when(F.col("valid_temperature"), 0.4).otherwise(0.0) +
                F.when(F.col("valid_humidity"), 0.2).otherwise(0.0) +
                F.when(F.col("valid_wind"), 0.2).otherwise(0.0) +
                F.when(F.col("valid_pressure"), 0.2).otherwise(0.0)
            )
        )


class EventQualityMixin:
    """
    Quality checks for event data.

    Can be mixed into silver/gold event consumers.
    """

    def validate_event_duration(self, df: DataFrame) -> DataFrame:
        """
        Validate event duration is reasonable.

        Args:
            df: DataFrame with event_start and event_end columns

        Returns:
            DataFrame with 'valid_duration' boolean column
        """
        # Events should be between 0 hours and 30 days
        df = df.withColumn(
            "duration_hours",
            F.when(
                (F.col("event_start").isNotNull()) &
                (F.col("event_end").isNotNull()),
                (F.unix_timestamp("event_end") - F.unix_timestamp("event_start")) / 3600.0
            ).otherwise(None)
        )

        return df.withColumn(
            "valid_duration",
            (F.col("duration_hours").isNotNull()) &
            (F.col("duration_hours") >= 0.0) &
            (F.col("duration_hours") <= 720.0)  # 30 days
        )

    def validate_event_location(self, df: DataFrame) -> DataFrame:
        """
        Validate event location (NYC bounds).

        Args:
            df: DataFrame with latitude and longitude columns

        Returns:
            DataFrame with 'valid_location' boolean column
        """
        # NYC geographic bounds
        return df.withColumn(
            "valid_location",
            (F.col("latitude").isNotNull()) &
            (F.col("longitude").isNotNull()) &
            (F.col("latitude") >= 40.4) &
            (F.col("latitude") <= 41.0) &
            (F.col("longitude") >= -74.3) &
            (F.col("longitude") <= -73.7)
        )

    def validate_affected_zones(self, df: DataFrame) -> DataFrame:
        """
        Validate affected zones count.

        Args:
            df: DataFrame with affected_zones array column

        Returns:
            DataFrame with 'valid_zones' boolean column
        """
        df = df.withColumn(
            "zone_count",
            F.when(F.col("affected_zones").isNotNull(), F.size("affected_zones")).otherwise(0)
        )

        # Events shouldn't affect more than 50 zones
        return df.withColumn(
            "valid_zones",
            (F.col("zone_count") >= 0) & (F.col("zone_count") <= 50)
        )

    def calculate_event_quality_score(self, df: DataFrame) -> DataFrame:
        """
        Calculate overall quality score for event records.

        Args:
            df: DataFrame with event data

        Returns:
            DataFrame with 'event_quality_score' column (0.0 to 1.0)
        """
        # Apply validations
        df = self.validate_event_duration(df)
        df = self.validate_event_location(df)
        df = self.validate_affected_zones(df)

        # Calculate weighted score
        return df.withColumn(
            "event_quality_score",
            (
                F.when(F.col("valid_duration"), 0.3).otherwise(0.0) +
                F.when(F.col("valid_location"), 0.4).otherwise(0.0) +
                F.when(F.col("valid_zones"), 0.3).otherwise(0.0)
            )
        )


class DataCompletenessMixin:
    """
    Generic data completeness checks.

    Can be mixed into any consumer for completeness validation.
    """

    def check_required_columns(self, df: DataFrame, required_cols: list) -> DataFrame:
        """
        Check if required columns have non-null values.

        Args:
            df: Input DataFrame
            required_cols: List of required column names

        Returns:
            DataFrame with 'completeness_score' column
        """
        # Count non-null values for required columns
        completeness_checks = []
        for col_name in required_cols:
            if col_name in df.columns:
                completeness_checks.append(
                    F.when(F.col(col_name).isNotNull(), 1.0).otherwise(0.0)
                )

        if completeness_checks:
            df = df.withColumn(
                "completeness_score",
                sum(completeness_checks) / len(completeness_checks)
            )
        else:
            df = df.withColumn("completeness_score", F.lit(0.0))

        return df

    def check_data_freshness(self, df: DataFrame, timestamp_col: str, max_age_hours: int = 24) -> DataFrame:
        """
        Check if data is fresh (not too old).

        Args:
            df: Input DataFrame
            timestamp_col: Timestamp column to check
            max_age_hours: Maximum age in hours

        Returns:
            DataFrame with 'is_fresh' boolean column
        """
        cutoff_time = F.date_sub(F.current_timestamp(), max_age_hours / 24.0)

        return df.withColumn(
            "is_fresh",
            (F.col(timestamp_col).isNotNull()) &
            (F.col(timestamp_col) >= cutoff_time)
        )