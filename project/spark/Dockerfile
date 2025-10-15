FROM apache/spark:3.5.0-java17

USER root

# Install additional dependencies
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Download Delta Lake JARs
RUN cd /opt/spark/jars && \
    curl -O https://repo1.maven.org/maven2/io/delta/delta-spark_2.12/3.2.0/delta-spark_2.12-3.2.0.jar && \
    curl -O https://repo1.maven.org/maven2/io/delta/delta-storage/3.2.0/delta-storage-3.2.0.jar

# Download Kafka connector JARs
RUN cd /opt/spark/jars && \
    curl -O https://repo1.maven.org/maven2/org/apache/spark/spark-sql-kafka-0-10_2.12/3.5.0/spark-sql-kafka-0-10_2.12-3.5.0.jar && \
    curl -O https://repo1.maven.org/maven2/org/apache/kafka/kafka-clients/3.7.0/kafka-clients-3.7.0.jar && \
    curl -O https://repo1.maven.org/maven2/org/apache/commons/commons-pool2/2.11.1/commons-pool2-2.11.1.jar

# Download Avro JARs
RUN cd /opt/spark/jars && \
    curl -O https://repo1.maven.org/maven2/org/apache/avro/avro/1.11.3/avro-1.11.3.jar && \
    curl -O https://packages.confluent.io/maven/io/confluent/kafka-avro-serializer/7.5.0/kafka-avro-serializer-7.5.0.jar && \
    curl -O https://packages.confluent.io/maven/io/confluent/kafka-schema-registry-client/7.5.0/kafka-schema-registry-client-7.5.0.jar && \
    curl -O https://packages.confluent.io/maven/io/confluent/common-config/7.5.0/common-config-7.5.0.jar && \
    curl -O https://packages.confluent.io/maven/io/confluent/common-utils/7.5.0/common-utils-7.5.0.jar

# Create checkpoint directories
RUN mkdir -p /tmp/checkpoint/bronze /tmp/checkpoint/silver /tmp/checkpoint/gold && \
    chmod -R 777 /tmp/checkpoint

# Set work directory
WORKDIR /opt/spark/work-dir

USER ${spark_uid}
