# Multi-stage build for STA

FROM gradle:8.11-jdk21 AS builder

WORKDIR /build

# Stage 1
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle

COPY schemas ./schemas
COPY producer ./producer

RUN gradle :producer:fatJar --no-daemon

# Stage 2
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /build/producer/build/libs/producer-1.0.0-all.jar /app/producer.jar

RUN groupadd -r producer && \
    useradd -r -g producer -m -s /sbin/nologin producer && \
    chown -R producer:producer /app

USER producer

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/producer.jar"]
