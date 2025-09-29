#!/usr/bin/env python3
"""
Trip data sources for Kafka producers.

Provides NYC TLC and synthetic trip data sources.
"""

import random
from typing import Generator

from .base import (
    DataSource,
    ConfigurableDataSource,
    DatabaseSourceMixin,
    SyntheticSourceMixin,
    DataFetcher,
    QueryBuilder
)

from ..utils import (
    build_parquet_urls,
    generate_date_list,
    generate_gaussian_value
)
from ..common import (
    NYC_TLC_BASE_URL,
    POPULAR_ZONE_PAIRS
)


class TripDataSource(ConfigurableDataSource, DataSource):
    """Base class for trip data sources."""
    pass


class NYCTLCDataSource(TripDataSource, DatabaseSourceMixin):
    """Real NYC TLC trip data source."""

    def __init__(self, config):
        """Initialize NYC TLC data source."""
        super().__init__(config)
        self.result = None

    def is_available(self) -> bool:
        """Check if NYC TLC data is accessible."""
        if self.config.synthetic_mode == "synthetic":
            return False

        urls = build_parquet_urls(
            NYC_TLC_BASE_URL,
            self.config.dataset,
            self.config.years,
            self.config.months
        )

        if not urls:
            return False

        # Build test query
        query = QueryBuilder.build_parquet_query(
            columns={"COUNT(*)": "cnt"},
            where_conditions=[
                f"trip_distance BETWEEN {self.config.min_dist} AND {self.config.max_dist}"
            ],
            limit=1
        )

        result = self.execute_duckdb_query(
            query,
            params=[urls[:1]],
            fetch_one=True,
            error_message="NYC TLC availability check failed"
        )
        return result is not None

    def fetch(self) -> Generator:
        """Fetch NYC TLC trip data."""
        urls = build_parquet_urls(
            NYC_TLC_BASE_URL,
            self.config.dataset,
            self.config.years,
            self.config.months
        )

        # Build main query
        query = QueryBuilder.build_parquet_query(
            columns={
                "tpep_pickup_datetime": "ts",
                "PULocationID": "pu",
                "DOLocationID": "do",
                "trip_distance": "dist"
            },
            where_conditions=[
                f"trip_distance BETWEEN {self.config.min_dist} AND {self.config.max_dist}",
                "tpep_dropoff_datetime > tpep_pickup_datetime"
            ],
            order_by="ts"
        )

        self.result = self.execute_duckdb_query(
            query,
            params=[urls],
            fetch_all=False,
            error_message="Failed to fetch NYC TLC data"
        )

        if self.result:
            yield from self.fetch_chunked_data(
                self.result,
                self.get_config_value('chunk_rows', 10000)
            )


class SyntheticTripSource(TripDataSource, SyntheticSourceMixin):
    """Synthetic trip data generator."""

    def is_available(self) -> bool:
        """Synthetic data is always available."""
        return True

    def fetch(self) -> Generator:
        """Generate synthetic trip data."""
        dates = generate_date_list(
            self.config.years,
            self.config.months
        )

        for date in dates:
            yield from self._generate_day_trips(date)

    def _generate_day_trips(self, date) -> Generator:
        """Generate trips for a single day."""
        import time
        weather = self.scenarios.generate_weather_conditions(date)
        weather_impact = self.scenarios.WEATHER_IMPACTS.get(
            weather["conditions"],
            {"trip_count": 1.0, "trip_distance": 1.0}
        )

        for hour in range(24):
            timestamp = date.replace(hour=hour)
            demand = self.scenarios.generate_trip_demand(timestamp, 5000)
            demand *= weather_impact["trip_count"]
            num_trips = int(generate_gaussian_value(
                demand,
                demand * 0.1,
                min_val=0
            ))

            trips_this_hour = []
            for _ in range(num_trips):
                trip = self._generate_single_trip(
                    timestamp,
                    hour,
                    weather_impact
                )
                trips_this_hour.append(trip)

            # Sort trips by dropoff time for realistic ordering
            trips_this_hour.sort(key=lambda x: x.get('dropoff_datetime', ''))

            # Yield trips with realistic timing
            for i, trip in enumerate(trips_this_hour):
                yield trip

                # Add small delays to simulate trips completing throughout the hour
                if not self.config.burst_mode and i < len(trips_this_hour) - 1:
                    # Distribute trips across the hour (compressed time)
                    # With ~100-500 trips per hour, use 1-5ms delays
                    delay = 0.5 / max(len(trips_this_hour), 1)  # 500ms spread across all trips
                    time.sleep(min(delay, 0.005))  # Cap at 5ms per trip

    def _generate_single_trip(self, timestamp, hour, weather_impact):
        """Generate a single trip record."""
        minute = random.randint(0, 59)
        second = random.randint(0, 59)
        trip_time = timestamp.replace(minute=minute, second=second)

        # Select zones based on patterns
        if hour in [7, 8, 17, 18, 19]:  # Rush hours
            pu, do, base_dist = random.choice(POPULAR_ZONE_PAIRS)
        else:
            pu = random.randint(1, 263)
            do = random.randint(1, 263)
            base_dist = random.uniform(1.0, 10.0)

        distance = max(0.3, base_dist * random.uniform(0.8, 1.2))
        distance *= weather_impact.get("trip_distance", 1.0)

        return (trip_time, pu, do, distance)


class MixedTripSource(TripDataSource):
    """Mixed real and synthetic trip data source."""

    def __init__(self, config):
        """Initialize mixed data source."""
        super().__init__(config)
        self.real_source = NYCTLCDataSource(config)
        self.synthetic_source = SyntheticTripSource(config)
        self.synthetic_pct = 0.0

    def is_available(self) -> bool:
        """Check if at least synthetic is available."""
        return self.synthetic_source.is_available()

    def fetch(self) -> Generator:
        """Fetch mixed real and synthetic data."""
        real_gen = None
        if self.real_source.is_available():
            real_gen = self.real_source.fetch()

        synthetic_gen = self.synthetic_source.fetch()

        # Use DataFetcher to mix sources
        mixed_gen = DataFetcher.mix_data_sources(
            real_gen,
            synthetic_gen,
            ratio=self.config.synthetic_ratio,
            chunk_size=self.get_config_value('chunk_rows', 10000)
        )

        # Track metrics
        total = 0
        synthetic = 0
        for record in mixed_gen:
            total += 1
            # Could track which source each record came from
            yield record

        # Update synthetic percentage
        if total > 0:
            self.synthetic_pct = (synthetic / total) * 100 if real_gen else 100.0