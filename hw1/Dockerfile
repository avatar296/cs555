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

WORKDIR /app

# Install netcat for health checks and service waiting
RUN apt-get update && apt-get install -y netcat-openbsd && \
    rm -rf /var/lib/apt/lists/*

# Copy built JAR from builder stage
COPY --from=builder /app/build/classes/java/main ./classes

# Create libs directory
RUN mkdir -p ./libs

# Copy entrypoint script
COPY docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

# Create directories for logs and data
RUN mkdir -p /app/logs /app/data

# Add a non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser && \
    chown -R appuser:appuser /app

USER appuser

# Set Java classpath
ENV CLASSPATH=/app/classes:/app/libs/*

# Use the entrypoint script
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]

# Default command (will be overridden by docker-compose)
CMD ["java", "csx55.overlay.node.Registry", "8080"]