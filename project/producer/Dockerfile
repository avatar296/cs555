# Multi-stage build for NYC Taxi Event Stream Producer
# Stage 1: Build the fat JAR using Gradle
FROM gradle:8.11-jdk21 AS builder

WORKDIR /build

# Copy Gradle configuration files
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Copy all source code (common and producer modules)
COPY common ./common
COPY producer ./producer
COPY consumer ./consumer

# Build the producer fat JAR
RUN gradle :producer:fatJar --no-daemon

# Stage 2: Create lightweight runtime image
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the fat JAR from builder stage
COPY --from=builder /build/producer/build/libs/producer-1.0.0-all.jar /app/producer.jar

# Create non-root user for security
RUN groupadd -r producer && \
    useradd -r -g producer -m -s /sbin/nologin producer && \
    chown -R producer:producer /app

USER producer

# Set JVM options for container environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Producer runs continuously by default (infinite events)
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/producer.jar"]
