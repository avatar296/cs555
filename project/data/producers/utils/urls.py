#!/usr/bin/env python3
"""
URL building utilities for data sources.
"""

from typing import List, Any
from pathlib import Path


def build_parquet_urls(base_url: str, dataset: str, years: str, months: str = "") -> List[str]:
    """
    Build URLs for Parquet files based on year/month pattern.

    Args:
        base_url: Base URL pattern
        dataset: Dataset name (e.g., "yellow")
        years: Comma-separated years
        months: Comma-separated months or empty

    Returns:
        List of URLs
    """
    year_list = [y.strip() for y in years.split(",") if y.strip()]

    if months:
        month_list = [m.strip().zfill(2) for m in months.split(",") if m.strip()]
    else:
        month_list = [f"{m:02d}" for m in range(1, 13)]

    urls = []
    for year in year_list:
        for month in month_list:
            url = f"{base_url}{dataset}_tripdata_{year}-{month}.parquet"
            urls.append(url)

    return urls


def get_schema_path(schema_name: str) -> Path:
    """
    Get full path to schema file.

    Args:
        schema_name: Name of schema file

    Returns:
        Path object to schema file
    """
    return Path(__file__).parent.parent.parent / "schemas" / schema_name


def ensure_directory(path: Path):
    """
    Ensure directory exists, create if necessary.

    Args:
        path: Directory path
    """
    path.mkdir(parents=True, exist_ok=True)