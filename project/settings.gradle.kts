/*
 * Spatial Temporal Analysis (STA) Project
 * CS X55 Term Project - NYC Taxi Streaming Analytics
 *
 * Medallion Architecture: Bronze → Silver → Gold
 */

rootProject.name = "sta"

// Shared modules
include("schemas")          // Avro schema definitions

// Data generation
include("producer")         // Synthetic data producer

// Lakehouse (Medallion Architecture)
include("lakehouse")                        // Parent project (grouping)
include("lakehouse:streaming")              // Lakehouse streaming utilities
include("lakehouse:schema-management")      // Table creation and schema management
include("lakehouse:bronze")                 // Raw ingestion from Kafka
include("lakehouse:silver")                 // Cleaned, transformed data
include("lakehouse:gold")                   // Analytics-ready aggregations
