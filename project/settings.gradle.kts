/*
 * Spatial Temporal Analysis (STA) Project
 * CS X55 Term Project - NYC Taxi Streaming Analytics
 *
 * Medallion Architecture: Bronze → Silver → Gold
 */

rootProject.name = "sta"

// Shared modules
include("common")           // Avro schemas
include("streaming-common") // Shared streaming utilities

// Data generation
include("producer")         // Synthetic data producer

// Lakehouse layers (Medallion Architecture)
include("bronze-layer")     // Raw ingestion from Kafka
include("silver-layer")     // Cleaned, transformed data
include("gold-layer")       // Analytics-ready aggregations
