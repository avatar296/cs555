#!/usr/bin/env python3
"""
Base classes and mixins for data sources.

Provides common functionality to eliminate code duplication.
"""

import sys
from abc import ABC, abstractmethod
from datetime import datetime, timedelta
from typing import Generator, Any, Optional, List, Dict


class DataSource(ABC):
    """Abstract base class for all data sources."""

    @abstractmethod
    def fetch(self) -> Generator:
        """Fetch data from the source."""
        pass

    @abstractmethod
    def is_available(self) -> bool:
        """Check if data source is available."""
        pass


class DatabaseSourceMixin:
    """
    Mixin for data sources that use DuckDB.

    Provides common database query execution patterns.
    """

    def execute_duckdb_query(
        self,
        query: str,
        params: Optional[List] = None,
        fetch_one: bool = False,
        fetch_all: bool = True,
        error_message: str = "Query failed"
    ) -> Any:
        """
        Execute a DuckDB query with error handling.

        Args:
            query: SQL query to execute
            params: Query parameters
            fetch_one: Return single result
            fetch_all: Return all results (if False, returns cursor)
            error_message: Custom error message prefix

        Returns:
            Query results or None if failed
        """
        try:
            from ..utils import get_duckdb_connection
            con = get_duckdb_connection()

            if params:
                result = con.execute(query, params)
            else:
                result = con.execute(query)

            if fetch_one:
                return result.fetchone()
            elif fetch_all:
                return result.fetchall()
            else:
                return result

        except Exception as e:
            print(f"{error_message}: {e}", file=sys.stderr)
            if "403" in str(e) or "404" in str(e):
                print(f"  Access denied or not found", file=sys.stderr)
            return None

    def check_data_availability(
        self,
        url: str,
        query_template: str,
        source_name: str = "data"
    ) -> bool:
        """
        Check if a data source is available using a test query.

        Args:
            url: URL or path to data source
            query_template: Query template with {url} placeholder
            source_name: Name for logging

        Returns:
            True if available, False otherwise
        """
        query = query_template.format(url=url)
        result = self.execute_duckdb_query(
            query,
            fetch_one=True,
            error_message=f"{source_name} availability check failed"
        )
        return result is not None

    def fetch_chunked_data(
        self,
        result_cursor,
        chunk_size: int = 10000
    ) -> Generator:
        """
        Fetch data in chunks from a result cursor.

        Args:
            result_cursor: DuckDB result cursor
            chunk_size: Number of rows per chunk

        Yields:
            Individual rows from chunks
        """
        while True:
            rows = result_cursor.fetchmany(chunk_size)
            if not rows:
                break
            for row in rows:
                yield row


class SyntheticSourceMixin:
    """
    Mixin for synthetic data sources.

    Provides common synthetic data generation utilities.
    """

    _scenarios = None  # Class-level cache for scenarios

    @property
    def scenarios(self):
        """
        Lazy-load and cache synthetic scenarios.

        Returns:
            Scenarios instance
        """
        if SyntheticSourceMixin._scenarios is None:
            from synthetic_scenarios import get_scenarios
            SyntheticSourceMixin._scenarios = get_scenarios()
        return SyntheticSourceMixin._scenarios

    def is_available(self) -> bool:
        """Synthetic data is always available."""
        return True

    def generate_weather_code(self, conditions: str) -> str:
        """
        Generate NOAA-style weather code from conditions.

        Args:
            conditions: Weather condition string

        Returns:
            NOAA weather code
        """
        if "rain" in conditions:
            return "RA+" if "heavy" in conditions else "RA"
        elif "snow" in conditions:
            return "SN+" if "heavy" in conditions else "SN"
        elif conditions == "fog":
            return "FG"
        return ""


class ContinuousDataMixin:
    """
    Mixin for data sources that support continuous mode.

    Provides common replay and cycling logic.
    """

    def should_continue(self, config) -> bool:
        """
        Check if continuous mode is enabled.

        Args:
            config: Configuration object

        Returns:
            True if should continue generating data
        """
        return hasattr(config, 'continuous_mode') and config.continuous_mode

    def adjust_timestamp_for_cycle(
        self,
        timestamp: Any,
        cycles: int,
        days_per_cycle: int = 365
    ) -> Any:
        """
        Adjust timestamp for data replay cycles.

        Args:
            timestamp: Original timestamp
            cycles: Number of cycles completed
            days_per_cycle: Days to advance per cycle

        Returns:
            Adjusted timestamp or original if adjustment fails
        """
        if cycles <= 0:
            return timestamp

        try:
            if isinstance(timestamp, str):
                dt = datetime.fromisoformat(timestamp)
            elif isinstance(timestamp, datetime):
                dt = timestamp
            else:
                return timestamp

            new_dt = dt + timedelta(days=days_per_cycle * cycles)

            if isinstance(timestamp, str):
                return new_dt.isoformat()
            return new_dt

        except Exception:
            return timestamp  # Return original if parsing fails

    def generate_continuous_data(
        self,
        data_generator: Generator,
        config,
        adjust_timestamps: bool = True
    ) -> Generator:
        """
        Wrap a generator to support continuous mode with replay.

        Args:
            data_generator: Base data generator
            config: Configuration object
            adjust_timestamps: Whether to adjust timestamps on replay

        Yields:
            Data records, potentially cycled
        """
        cycles = 0
        data_cache = []

        # First pass - cache and yield
        for item in data_generator:
            data_cache.append(item)
            yield item

        # Check if we should continue
        if not self.should_continue(config):
            return

        # Replay cached data with adjustments
        while self.should_continue(config):
            cycles += 1
            if cycles == 1:
                print("Data exhausted, continuing with replay...", file=sys.stderr)

            for item in data_cache:
                if adjust_timestamps and isinstance(item, dict) and 'DATE' in item:
                    item = {
                        **item,
                        'DATE': self.adjust_timestamp_for_cycle(
                            item['DATE'],
                            cycles
                        )
                    }
                yield item


class ConfigurableDataSource:
    """
    Base class for data sources with configuration.

    Provides config storage and access patterns.
    """

    def __init__(self, config):
        """
        Initialize with configuration.

        Args:
            config: Configuration object
        """
        self.config = config

    def get_config_value(self, key: str, default: Any = None) -> Any:
        """
        Get configuration value with default.

        Args:
            key: Configuration key
            default: Default value if not found

        Returns:
            Configuration value or default
        """
        return getattr(self.config, key, default)


class DataFetcher:
    """
    Utility class for common data fetching patterns.
    """

    @staticmethod
    def mix_data_sources(
        primary_gen: Optional[Generator],
        secondary_gen: Generator,
        ratio: float = 0.5,
        chunk_size: int = 1000
    ) -> Generator:
        """
        Mix two data sources with a specified ratio.

        Args:
            primary_gen: Primary data generator (can be None)
            secondary_gen: Secondary data generator
            ratio: Ratio of secondary data (0-1)
            chunk_size: Size of chunks to process

        Yields:
            Mixed data records
        """
        import random

        if not primary_gen:
            # Pure secondary
            yield from secondary_gen
            return

        while True:
            primary_batch = []
            secondary_batch = []

            # Get primary data
            if ratio < 1.0:
                try:
                    for _ in range(int(chunk_size * (1 - ratio))):
                        primary_batch.append(next(primary_gen))
                except StopIteration:
                    pass

            # Get secondary data
            if ratio > 0:
                try:
                    for _ in range(int(chunk_size * ratio)):
                        secondary_batch.append(next(secondary_gen))
                except StopIteration:
                    pass

            # Combine and shuffle
            combined = primary_batch + secondary_batch
            if not combined:
                break

            random.shuffle(combined)
            for record in combined:
                yield record


class QueryBuilder:
    """
    Utility class for building DuckDB queries safely.
    """

    @staticmethod
    def build_parquet_query(
        columns: Dict[str, str],
        table: str = "?",
        where_conditions: List[str] = None,
        order_by: str = None,
        limit: int = None
    ) -> str:
        """
        Build a SELECT query for Parquet files.

        Args:
            columns: Dict mapping select expressions to aliases
            table: Table expression (default "?" for parameterized)
            where_conditions: WHERE clause conditions
            order_by: ORDER BY clause
            limit: LIMIT clause

        Returns:
            SQL query string
        """
        # Build SELECT clause
        select_parts = [f"{expr} AS {alias}" for expr, alias in columns.items()]
        query = f"SELECT {', '.join(select_parts)}\n"

        # Add FROM clause
        query += f"FROM read_parquet({table}, filename=true)\n"

        # Add WHERE clause
        if where_conditions:
            query += f"WHERE {' AND '.join(where_conditions)}\n"

        # Add ORDER BY
        if order_by:
            query += f"ORDER BY {order_by}\n"

        # Add LIMIT
        if limit:
            query += f"LIMIT {limit}\n"

        return query

    @staticmethod
    def build_csv_query(
        url: str,
        columns: List[str] = None,
        where_conditions: List[str] = None,
        options: Dict[str, Any] = None
    ) -> str:
        """
        Build a SELECT query for CSV files.

        Args:
            url: CSV file URL
            columns: Columns to select (None for all)
            where_conditions: WHERE clause conditions
            options: CSV read options

        Returns:
            SQL query string
        """
        # Default options
        default_options = {
            'header': 'true',
            'delim': "','",
            'ignore_errors': 'true'
        }

        if options:
            default_options.update(options)

        # Build options string
        option_parts = [f"{k}={v}" for k, v in default_options.items()]

        # Build query
        select_clause = ', '.join(columns) if columns else '*'
        query = f"SELECT {select_clause} FROM read_csv_auto('{url}',\n"
        query += f"    {', '.join(option_parts)}\n)\n"

        if where_conditions:
            query += f"WHERE {' AND '.join(where_conditions)}\n"

        return query