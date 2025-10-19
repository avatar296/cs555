FROM eclipse-temurin:17-jre

USER root

# Install system dependencies
RUN apt-get update && apt-get install -y \
    curl \
    bash \
    procps \
    && rm -rf /var/lib/apt/lists/*

# Download and install Spark 3.5.0
ENV SPARK_VERSION=3.5.0
ENV HADOOP_VERSION=3
ENV SPARK_HOME=/opt/spark

RUN curl -fsSL https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz \
    | tar -xz -C /opt && \
    mv /opt/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION} ${SPARK_HOME}

# Download Apache Iceberg JARs
RUN cd ${SPARK_HOME}/jars && \
    curl -O https://repo1.maven.org/maven2/org/apache/iceberg/iceberg-spark-runtime-3.5_2.12/1.6.1/iceberg-spark-runtime-3.5_2.12-1.6.1.jar

# Download Hive Metastore Standalone + PostgreSQL JDBC for catalog
RUN cd ${SPARK_HOME}/jars && \
    curl -O https://repo1.maven.org/maven2/org/apache/hive/hive-metastore/3.1.3/hive-metastore-3.1.3.jar && \
    curl -O https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.1/postgresql-42.7.1.jar

# Download Hadoop AWS + S3A for MinIO support
RUN cd ${SPARK_HOME}/jars && \
    curl -O https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-aws/3.3.4/hadoop-aws-3.3.4.jar && \
    curl -O https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-bundle/1.12.262/aws-java-sdk-bundle-1.12.262.jar

# Download Kafka connector dependencies (pre-baked for production, no --packages needed)
RUN cd ${SPARK_HOME}/jars && \
    curl -O https://repo1.maven.org/maven2/org/apache/spark/spark-sql-kafka-0-10_2.12/3.5.0/spark-sql-kafka-0-10_2.12-3.5.0.jar && \
    curl -O https://repo1.maven.org/maven2/org/apache/spark/spark-token-provider-kafka-0-10_2.12/3.5.0/spark-token-provider-kafka-0-10_2.12-3.5.0.jar && \
    curl -O https://repo1.maven.org/maven2/org/apache/kafka/kafka-clients/3.4.1/kafka-clients-3.4.1.jar && \
    curl -O https://repo1.maven.org/maven2/org/apache/commons/commons-pool2/2.11.1/commons-pool2-2.11.1.jar && \
    curl -O https://repo1.maven.org/maven2/org/lz4/lz4-java/1.8.0/lz4-java-1.8.0.jar && \
    curl -O https://repo1.maven.org/maven2/org/xerial/snappy/snappy-java/1.1.10.3/snappy-java-1.1.10.3.jar && \
    curl -O https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-client-runtime/3.3.4/hadoop-client-runtime-3.3.4.jar && \
    curl -O https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-client-api/3.3.4/hadoop-client-api-3.3.4.jar

# Download Spark Avro (must match Spark version 3.5.0)
RUN cd ${SPARK_HOME}/jars && \
    curl -O https://repo1.maven.org/maven2/org/apache/spark/spark-avro_2.12/3.5.0/spark-avro_2.12-3.5.0.jar && \
    curl -O https://repo1.maven.org/maven2/org/tukaani/xz/1.8/xz-1.8.jar && \
    curl -O https://repo1.maven.org/maven2/org/spark-project/spark/unused/1.0.0/unused-1.0.0.jar

# Download ABRiS for Confluent Schema Registry support
RUN cd ${SPARK_HOME}/jars && \
    curl -O https://repo1.maven.org/maven2/za/co/absa/abris_2.12/6.4.0/abris_2.12-6.4.0.jar && \
    curl -O https://repo1.maven.org/maven2/za/co/absa/commons/commons_2.12/1.0.0/commons_2.12-1.0.0.jar

# Download Confluent Schema Registry dependencies (from Confluent Maven repo)
RUN cd ${SPARK_HOME}/jars && \
    curl -O https://packages.confluent.io/maven/io/confluent/kafka-avro-serializer/6.2.1/kafka-avro-serializer-6.2.1.jar && \
    curl -O https://packages.confluent.io/maven/io/confluent/kafka-schema-registry-client/6.2.1/kafka-schema-registry-client-6.2.1.jar && \
    curl -O https://packages.confluent.io/maven/io/confluent/kafka-schema-serializer/6.2.1/kafka-schema-serializer-6.2.1.jar && \
    curl -O https://packages.confluent.io/maven/io/confluent/common-utils/6.2.1/common-utils-6.2.1.jar && \
    curl -O https://packages.confluent.io/maven/io/confluent/common-config/6.2.1/common-config-6.2.1.jar

# Download supporting dependencies for Schema Registry client
RUN cd ${SPARK_HOME}/jars && \
    curl -O https://repo1.maven.org/maven2/org/apache/avro/avro/1.10.2/avro-1.10.2.jar && \
    curl -O https://repo1.maven.org/maven2/org/apache/commons/commons-compress/1.21/commons-compress-1.21.jar && \
    curl -O https://repo1.maven.org/maven2/jakarta/ws/rs/jakarta.ws.rs-api/2.1.6/jakarta.ws.rs-api-2.1.6.jar && \
    curl -O https://repo1.maven.org/maven2/org/glassfish/jersey/core/jersey-common/2.34/jersey-common-2.34.jar && \
    curl -O https://repo1.maven.org/maven2/jakarta/annotation/jakarta.annotation-api/1.3.5/jakarta.annotation-api-1.3.5.jar && \
    curl -O https://repo1.maven.org/maven2/org/glassfish/hk2/external/jakarta.inject/2.6.1/jakarta.inject-2.6.1.jar && \
    curl -O https://repo1.maven.org/maven2/org/glassfish/hk2/osgi-resource-locator/1.0.3/osgi-resource-locator-1.0.3.jar

# Create spark user
RUN groupadd -r spark -g 185 && \
    useradd -u 185 -r -g spark -m -s /sbin/nologin spark && \
    chown -R spark:spark ${SPARK_HOME}

# Create checkpoint directories
RUN mkdir -p /tmp/checkpoint/bronze /tmp/checkpoint/silver /tmp/checkpoint/gold && \
    chmod -R 777 /tmp/checkpoint

# Set environment variables
ENV PATH=${SPARK_HOME}/bin:${SPARK_HOME}/sbin:${PATH}

# Set work directory
WORKDIR /opt/spark/work-dir

USER spark
