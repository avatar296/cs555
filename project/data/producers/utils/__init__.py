#!/usr/bin/env python3
"""
Utilities package for Kafka producers.
"""

# Database utilities
from .database import (
    get_duckdb_connection,
    close_duckdb_connection
)

# Date utilities
from .dates import (
    parse_date_range,
    generate_date_list,
    add_lateness,
    normalize_timestamp
)

# Key utilities
from .keys import (
    generate_salted_key,
    generate_hour_of_week_key
)

# Data utilities
from .data import (
    validate_distance,
    validate_coordinates,
    batch_generator,
    calculate_sleep_time,
    random_choice_weighted,
    generate_gaussian_value
)

# URL utilities
from .urls import (
    build_parquet_urls,
    get_schema_path,
    ensure_directory
)

__all__ = [
    # Database
    'get_duckdb_connection',
    'close_duckdb_connection',

    # Dates
    'parse_date_range',
    'generate_date_list',
    'add_lateness',
    'normalize_timestamp',

    # Keys
    'generate_salted_key',
    'generate_hour_of_week_key',

    # Data
    'validate_distance',
    'validate_coordinates',
    'batch_generator',
    'calculate_sleep_time',
    'random_choice_weighted',
    'generate_gaussian_value',

    # URLs
    'build_parquet_urls',
    'get_schema_path',
    'ensure_directory'
]