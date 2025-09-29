# Medallion Architecture - Consumer Design

## Overview

This consumer framework implements a clean, extensible medallion architecture following SOLID and DRY principles. The design enables code reuse across bronze, silver, and gold layers while maintaining clear separation of concerns.

## Architecture Principles

### SOLID Principles Applied

1. **Single Responsibility**: Each class has one reason to change
   - `BaseStreamConsumer`: Handles streaming infrastructure
   - `BronzeConsumer`: Raw data preservation only
   - `SilverConsumer`: Data quality and cleansing only
   - `GoldConsumer`: Business aggregations only

2. **Open/Closed**: Extended without modification
   - Base classes provide extension points via abstract methods
   - New consumers extend behavior without changing base code

3. **Liskov Substitution**: Subclasses can replace base classes
   - All consumers follow consistent interface
   - Can swap implementations transparently

4. **Interface Segregation**: No forced implementations
   - Each layer implements only what it needs
   - Mixins provide optional functionality

5. **Dependency Inversion**: Depend on abstractions
   - Concrete consumers depend on abstract base classes
   - Configuration injected, not hardcoded

### DRY (Don't Repeat Yourself)

- **Quality Mixins**: Reusable validation logic
- **Base Classes**: Common streaming logic
- **Template Method**: Consistent processing pipeline

## Class Hierarchy

```
BaseStreamConsumer (Abstract)
    ├── BronzeConsumer
    │   └── BronzeTripsConsumer (concrete)
    │   └── BronzeWeatherConsumer (concrete)
    │   └── BronzeEventsConsumer (concrete)
    │
    ├── SilverConsumer
    │   └── SilverTripsConsumer (with TripQualityMixin)
    │   └── SilverWeatherConsumer (with WeatherQualityMixin)
    │   └── SilverEventsConsumer (with EventQualityMixin)
    │
    └── GoldConsumer
        └── GoldTripsAggregateConsumer
        └── GoldWeatherKPIConsumer
        └── GoldEventImpactConsumer

Quality Mixins (Reusable):
    ├── TripQualityMixin
    ├── WeatherQualityMixin
    ├── EventQualityMixin
    └── DataCompletenessMixin
```

## Layer Responsibilities

### Bronze Layer
- **Purpose**: Raw data preservation
- **Transformations**: None
- **Validation**: None
- **Partitioning**: By ingestion timestamp
- **Output Mode**: Append only

### Silver Layer
- **Purpose**: Cleaned, validated data
- **Transformations**:
  - Data type normalization
  - Deduplication
  - Outlier handling
- **Validation**:
  - Quality scoring
  - Completeness checks
  - Business rule validation
- **Partitioning**: By event timestamp
- **Output Mode**: Append (after filtering)

### Gold Layer
- **Purpose**: Business-ready aggregations
- **Transformations**:
  - Aggregations
  - KPI calculations
  - Dimension joins
- **Validation**: Business rules
- **Partitioning**: By aggregation window
- **Output Mode**: Update or Complete

## Template Method Pattern

The `BaseStreamConsumer.process_batch()` method defines the processing pipeline:

```python
def process_batch(self, df):
    df = self.add_metadata(df)    # Layer-specific metadata
    df = self.validate(df)         # Layer-specific validation
    df = self.transform(df)        # Layer-specific transformation
    return df
```

Each layer implements these methods differently:

| Method | Bronze | Silver | Gold |
|--------|--------|--------|------|
| `add_metadata()` | Ingestion time, consumer ID | Processing time, quality score | Aggregation window, KPIs |
| `validate()` | Pass through | Quality checks, filtering | Business rules |
| `transform()` | Pass through | Cleanse, dedupe, normalize | Aggregate, join, calculate |

## Using Quality Mixins

Mixins provide reusable quality checks:

```python
class SilverTripsConsumer(SilverConsumer, TripQualityMixin):
    def calculate_quality_score(self, df):
        # Use mixin methods
        df = self.validate_trip_distance(df)
        df = self.validate_trip_locations(df)
        df = self.calculate_trip_quality_score(df)
        return df
```

## Configuration Hierarchy

```python
BaseConsumerConfig
    ├── BronzeConfig (ingestion-focused)
    ├── SilverConfig (quality-focused)
    │   ├── quality_threshold
    │   └── dedup_window
    └── GoldConfig (aggregation-focused)
        ├── aggregation_window
        └── watermark
```

## Extension Points

### Adding New Data Sources

1. Create concrete consumer extending appropriate layer:
```python
class BronzeNewSourceConsumer(BronzeConsumer):
    # Inherits all bronze behavior
    pass
```

2. Add quality checks via mixin:
```python
class NewSourceQualityMixin:
    def validate_new_source(self, df):
        # Custom validation logic
        pass
```

3. Create silver consumer with quality:
```python
class SilverNewSourceConsumer(SilverConsumer, NewSourceQualityMixin):
    def calculate_quality_score(self, df):
        return self.validate_new_source(df)
```

### Adding New Quality Checks

Simply add methods to existing mixins or create new mixins:

```python
class EnhancedTripQualityMixin(TripQualityMixin):
    def validate_trip_speed(self, df):
        # Additional validation
        pass
```

## Benefits of This Architecture

1. **Maintainability**: Clear separation of concerns
2. **Testability**: Each component can be tested independently
3. **Reusability**: Mixins and base classes promote code reuse
4. **Extensibility**: Easy to add new layers or data sources
5. **Consistency**: Template method ensures uniform processing
6. **Flexibility**: Can mix and match quality checks as needed

## Migration Path

To migrate existing bronze consumers:

1. Extend `BronzeConsumer` instead of `BaseBronzeConsumer`
2. Remove `process_batch()` override (uses parent)
3. Configuration remains the same
4. Functionality unchanged, architecture improved

## Example: Complete Pipeline

```python
# Bronze: Raw preservation
bronze = BronzeTripsConsumer(config)
bronze.run()  # Writes to bronze.trips

# Silver: Quality & cleansing
silver = SilverTripsConsumer(
    config=TripsConsumerConfig(
        source_topic="bronze.trips",
        target_table="silver.trips"
    )
)
silver.run()  # Reads bronze, writes silver

# Gold: Aggregations
gold = GoldTripsAggregateConsumer(
    config=TripsConsumerConfig(
        source_topic="silver.trips",
        target_table="gold.trips_hourly"
    )
)
gold.run()  # Reads silver, writes gold
```

This architecture provides a solid foundation for building robust, maintainable data pipelines following best practices.