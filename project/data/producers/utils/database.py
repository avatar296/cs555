#!/usr/bin/env python3
"""
Database utilities for DuckDB connection management.
"""

import duckdb
from ..common.schemas import DUCKDB_SETUP

# DuckDB Connection Management
_duckdb_conn = None


def get_duckdb_connection(reset: bool = False) -> duckdb.DuckDBPyConnection:
    """
    Get or create a singleton DuckDB connection.

    Args:
        reset: Force create a new connection

    Returns:
        DuckDB connection instance
    """
    global _duckdb_conn

    if reset or _duckdb_conn is None:
        if _duckdb_conn:
            _duckdb_conn.close()

        _duckdb_conn = duckdb.connect()
        _duckdb_conn.execute(DUCKDB_SETUP)

    return _duckdb_conn


def close_duckdb_connection():
    """Close the singleton DuckDB connection if it exists."""
    global _duckdb_conn
    if _duckdb_conn:
        _duckdb_conn.close()
        _duckdb_conn = None