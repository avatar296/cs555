#!/usr/bin/env python3
"""
Gold Layer Consumers

Business-level aggregations and KPIs from silver layer data.
Demonstrates simple to complex streaming patterns:
- Simple: Single source aggregation
- Medium: Aggregation with dimension enrichment
- Complex: Multi-stream joins with temporal + spatial alignment
"""

from .zone_metrics_hourly import GoldZoneMetricsHourly
from .od_flows_enriched_hourly import GoldODFlowsEnrichedHourly
from .zone_demand_context_hourly import GoldZoneDemandContextHourly

__all__ = [
    "GoldZoneMetricsHourly",
    "GoldODFlowsEnrichedHourly",
    "GoldZoneDemandContextHourly",
]
