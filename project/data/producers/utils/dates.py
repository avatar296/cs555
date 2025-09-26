#!/usr/bin/env python3
"""
Date and time utilities for data processing.
"""

from datetime import datetime, timedelta
from typing import List, Tuple


def parse_date_range(years: str, months: str = "") -> Tuple[datetime, datetime]:
    """
    Parse year and month strings into date range.

    Args:
        years: Comma-separated years (e.g., "2023,2024")
        months: Comma-separated months (e.g., "1,2,3") or empty for all

    Returns:
        Tuple of (start_date, end_date)
    """
    year_list = [int(y.strip()) for y in years.split(",") if y.strip()]
    if not year_list:
        year_list = [datetime.now().year]

    if months:
        month_list = [int(m.strip()) for m in months.split(",") if m.strip()]
    else:
        month_list = list(range(1, 13))

    # Calculate start date
    start_year = min(year_list)
    start_month = min(month_list) if months else 1
    start_date = datetime(start_year, start_month, 1)

    # Calculate end date
    end_year = max(year_list)
    end_month = max(month_list) if months else 12

    # Get last day of end month
    if end_month == 12:
        end_date = datetime(end_year + 1, 1, 1) - timedelta(days=1)
    else:
        end_date = datetime(end_year, end_month + 1, 1) - timedelta(days=1)

    return start_date, end_date


def generate_date_list(years: str, months: str = "") -> List[datetime]:
    """
    Generate list of dates for given years and months.

    Args:
        years: Comma-separated years
        months: Comma-separated months or empty

    Returns:
        List of datetime objects for each day
    """
    start_date, end_date = parse_date_range(years, months)
    dates = []

    current = start_date
    while current <= end_date:
        dates.append(current)
        current += timedelta(days=1)

    return dates


def add_lateness(
    timestamp: datetime,
    probability: float,
    min_seconds: int,
    max_seconds: int
) -> datetime:
    """
    Add random lateness to a timestamp.

    Args:
        timestamp: Original timestamp
        probability: Probability of adding lateness (0-1)
        min_seconds: Minimum lateness in seconds
        max_seconds: Maximum lateness in seconds

    Returns:
        Potentially delayed timestamp
    """
    import random

    if probability <= 0 or random.random() > probability:
        return timestamp

    delay = random.randint(min_seconds, max_seconds)
    return timestamp + timedelta(seconds=delay)


def normalize_timestamp(ts) -> datetime:
    """
    Normalize various timestamp formats to datetime.

    Args:
        ts: Timestamp in various formats

    Returns:
        datetime object
    """
    if isinstance(ts, datetime):
        return ts
    elif isinstance(ts, str):
        # Try ISO format first
        try:
            return datetime.fromisoformat(ts.replace("Z", "+00:00"))
        except:
            # Try other common formats
            for fmt in ["%Y-%m-%d %H:%M:%S", "%Y-%m-%d", "%m/%d/%Y"]:
                try:
                    return datetime.strptime(ts, fmt)
                except:
                    continue
    elif hasattr(ts, "timestamp"):
        # pandas Timestamp or similar
        return datetime.fromtimestamp(ts.timestamp())

    raise ValueError(f"Cannot normalize timestamp: {ts}")