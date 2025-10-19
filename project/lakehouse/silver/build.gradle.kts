plugins {
    java
    application
}

val slf4jVersion: String by rootProject.extra
val logbackVersion: String by rootProject.extra

// Spark and Iceberg versions
val sparkVersion = "3.5.0"
val icebergVersion = "1.6.1"
val hadoopVersion = "3.3.4"

dependencies {
    // Shared modules
    implementation(rootProject.project(":schemas"))
    implementation(project(":lakehouse:streaming"))

    // Apache Spark
    implementation("org.apache.spark:spark-sql_2.12:$sparkVersion")
    implementation("org.apache.spark:spark-streaming_2.12:$sparkVersion")
    implementation("org.apache.spark:spark-avro_2.12:$sparkVersion")

    // Apache Iceberg
    implementation("org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:$icebergVersion")

    // Hadoop AWS for MinIO S3 support
    implementation("org.apache.hadoop:hadoop-aws:$hadoopVersion")
    implementation("com.amazonaws:aws-java-sdk-bundle:1.12.262")

    // Logging
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
}

application {
    mainClass.set("csx55.sta.silver.SilverLayerApp")
}

// Create fat JAR for Spark submission
tasks.jar {
    archiveBaseName.set("silver-layer")
    archiveVersion.set("")

    // Enable zip64 for large JARs
    isZip64 = true

    manifest {
        attributes["Main-Class"] = "csx55.sta.silver.SilverLayerApp"
    }

    // Include dependencies (fat jar)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
