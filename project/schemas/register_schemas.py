#!/usr/bin/env python3
"""
Production Schema Registry management script.
Registers, updates, and validates schemas with version control.
"""

import json
import sys
import os
from pathlib import Path
from confluent_kafka.schema_registry import SchemaRegistryClient, Schema
from confluent_kafka.schema_registry.avro import AvroSerializer

SCHEMA_REGISTRY_URL = os.getenv("SCHEMA_REGISTRY_URL", "http://localhost:8082")

def load_schema(file_path):
    """Load schema from file."""
    with open(file_path, 'r') as f:
        return json.load(f)

def register_schema(client, subject, schema_str, compatibility="BACKWARD"):
    """
    Register or update a schema with compatibility checking.

    Args:
        client: SchemaRegistryClient instance
        subject: Subject name (typically topic-name-value or topic-name-key)
        schema_str: Avro schema as string
        compatibility: BACKWARD, FORWARD, FULL, NONE
    """
    try:
        # Set compatibility level
        client.set_compatibility(subject, compatibility)

        # Register schema
        schema = Schema(schema_str, schema_type="AVRO")
        schema_id = client.register_schema(subject, schema)

        print(f"✓ Registered schema for {subject}")
        print(f"  Schema ID: {schema_id}")
        print(f"  Compatibility: {compatibility}")

        # Get version info
        version = client.get_latest_version(subject)
        print(f"  Version: {version.version}")

        return schema_id

    except Exception as e:
        print(f"✗ Failed to register {subject}: {e}", file=sys.stderr)
        return None

def validate_compatibility(client, subject, new_schema_str):
    """Check if new schema is compatible with existing."""
    try:
        schema = Schema(new_schema_str, schema_type="AVRO")
        is_compatible = client.test_compatibility(subject, schema)
        return is_compatible
    except Exception as e:
        print(f"Compatibility check failed: {e}", file=sys.stderr)
        return False

def main():
    """Register all schemas in the schemas directory."""

    # Connect to Schema Registry
    try:
        client = SchemaRegistryClient({'url': SCHEMA_REGISTRY_URL})
        print(f"Connected to Schema Registry at {SCHEMA_REGISTRY_URL}\n")
    except Exception as e:
        print(f"Failed to connect to Schema Registry: {e}", file=sys.stderr)
        sys.exit(1)

    # Find all schema files
    schema_dir = Path(__file__).parent
    schema_files = list(schema_dir.glob("*.avsc"))

    if not schema_files:
        print("No schema files (*.avsc) found")
        return

    print(f"Found {len(schema_files)} schema file(s)\n")

    # Register each schema
    for schema_file in schema_files:
        print(f"Processing {schema_file.name}...")

        # Load schema
        schema_dict = load_schema(schema_file)
        schema_str = json.dumps(schema_dict)

        # Derive subject name from filename
        # Map schema files to their correct Kafka topic subjects
        if "trip_event" in schema_file.stem:
            subjects = [
                ("trips.yellow-value", "BACKWARD"),
                ("trips.green-value", "BACKWARD"),
            ]
        elif "weather_event" in schema_file.stem:
            subjects = [("weather.updates-value", "BACKWARD")]
        elif "event_calendar" in schema_file.stem:
            subjects = [("events.calendar-value", "BACKWARD")]
        else:
            # Default naming convention for any other schemas
            subjects = [(f"{schema_file.stem}-value", "BACKWARD")]

        for subject, compatibility in subjects:
            # Check compatibility if schema exists
            try:
                existing = client.get_latest_version(subject)
                print(f"  Existing schema found for {subject} (v{existing.version})")

                if validate_compatibility(client, subject, schema_str):
                    print(f"  ✓ New schema is compatible")
                else:
                    print(f"  ⚠ New schema is NOT compatible - review required")
                    continue
            except Exception:
                print(f"  New subject - will create {subject}")

            # Register schema
            register_schema(client, subject, schema_str, compatibility)

        print()

    # List all registered subjects
    print("\n=== Registered Subjects ===")
    subjects = client.get_subjects()
    for subject in subjects:
        version = client.get_latest_version(subject)
        print(f"  • {subject} (v{version.version}, id={version.schema_id})")

    print("\n✓ Schema registration complete")

if __name__ == "__main__":
    main()