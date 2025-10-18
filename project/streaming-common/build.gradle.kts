plugins {
    `java-library`
}

val slf4jVersion: String by rootProject.extra
val logbackVersion: String by rootProject.extra

// Spark and Iceberg versions
val sparkVersion = "3.5.0"
val icebergVersion = "1.6.1"
val hadoopVersion = "3.3.4"
val typesafeConfigVersion = "1.4.3"

dependencies {
    // Common module (Avro schemas)
    api(project(":common"))

    // Apache Spark (provided scope - will be available in cluster)
    compileOnly("org.apache.spark:spark-sql_2.12:$sparkVersion")
    compileOnly("org.apache.spark:spark-streaming_2.12:$sparkVersion")
    compileOnly("org.apache.spark:spark-sql-kafka-0-10_2.12:$sparkVersion")
    compileOnly("org.apache.spark:spark-avro_2.12:$sparkVersion")

    // Apache Iceberg
    compileOnly("org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:$icebergVersion")

    // Hadoop AWS for MinIO S3 support
    compileOnly("org.apache.hadoop:hadoop-aws:$hadoopVersion")
    compileOnly("com.amazonaws:aws-java-sdk-bundle:1.12.262")

    // Configuration management
    implementation("com.typesafe:config:$typesafeConfigVersion")

    // Logging
    api("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
}
