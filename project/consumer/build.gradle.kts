plugins {
    java
    application
}

val slf4jVersion: String by rootProject.extra

dependencies {
    // Common module
    implementation(project(":common"))

    // Logging
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}

application {
    mainClass.set("csx55.sta.consumer.ConsumerApp")
}

// NOTE: Consumer is currently a stub. Spark dependencies will be added later.
// When implementing, add:
//   - Spark SQL, Spark Streaming, Spark Avro
//   - Delta Lake
//   - Kafka connectors
