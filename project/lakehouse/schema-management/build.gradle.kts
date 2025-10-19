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

    // Apache Spark (provided by cluster, not bundled in JAR)
    compileOnly("org.apache.spark:spark-sql_2.12:$sparkVersion")
    compileOnly("org.apache.spark:spark-streaming_2.12:$sparkVersion")
    compileOnly("org.apache.spark:spark-sql-kafka-0-10_2.12:$sparkVersion")
    compileOnly("org.apache.spark:spark-avro_2.12:$sparkVersion")

    // Apache Iceberg (provided by cluster)
    compileOnly("org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:$icebergVersion")

    // Hadoop AWS (provided by cluster)
    compileOnly("org.apache.hadoop:hadoop-aws:$hadoopVersion")
    compileOnly("com.amazonaws:aws-java-sdk-bundle:1.12.262")

    // ABRiS (provided via --packages)
    compileOnly("za.co.absa:abris_2.12:6.4.0")

    // Logging
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
}

application {
    mainClass.set("csx55.sta.schema.SchemaManagementApp")
}

// Create fat JAR for Spark submission
tasks.jar {
    dependsOn(":lakehouse:streaming:jar")

    archiveBaseName.set("schema-management")
    archiveVersion.set("")

    // Enable zip64 for large JARs
    isZip64 = true

    manifest {
        attributes["Main-Class"] = "csx55.sta.schema.SchemaManagementApp"
    }

    // Include dependencies (fat jar)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
