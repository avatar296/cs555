# Multi-stage build for Bronze Layer

FROM gradle:8.11-jdk21 AS builder

WORKDIR /build

# Stage 1: Build the JAR
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle

COPY schemas ./schemas
COPY lakehouse/streaming ./lakehouse/streaming
COPY lakehouse/bronze ./lakehouse/bronze

RUN gradle :lakehouse:bronze:jar --no-daemon

# Stage 2: Copy into Spark image
FROM cowartc3/sta-spark:latest

USER root

# Copy the built JAR into the Spark image
COPY --from=builder /build/lakehouse/bronze/build/libs/bronze.jar /opt/spark/work-dir/jars/bronze.jar

RUN chown spark:spark /opt/spark/work-dir/jars/bronze.jar

USER spark

WORKDIR /opt/spark/work-dir
