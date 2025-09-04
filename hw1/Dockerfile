# Multi-stage build for Java application
# Build stage
FROM gradle:8.3-jdk11 AS builder

WORKDIR /app

# Copy gradle files first for better caching
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./

# Download dependencies
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src ./src

# Build the application
RUN ./gradlew build --no-daemon

# Runtime stage
FROM openjdk:11-jre-slim

# Set the working directory for the application
WORKDIR /app

# Install netcat for health checks and service waiting
RUN apt-get update && apt-get install -y netcat-openbsd && \
    rm -rf /var/lib/apt/lists/*

# Copy built classes from the builder stage
COPY --from=builder /app/build/classes/java/main .

# Create directories for logs and data, and set permissions
RUN mkdir -p /app/logs /app/data && \
    groupadd -r appuser && useradd -r -g appuser appuser && \
    chown -R appuser:appuser /app

USER appuser

# Default command - will be overridden by docker-compose, but good practice
CMD ["java", "-cp", "/app", "csx55.overlay.node.Registry", "8080"]