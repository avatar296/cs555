/*
 * Spatial Temporal Analysis (STA) Project
 * CS X55 Term Project - NYC Taxi Streaming Analytics
 */

plugins {
    java
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1" apply false
}

allprojects {
    group = "csx55.sta"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://packages.confluent.io/maven/")
    }
}

subprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}

// Version catalog for dependency management
val kafkaVersion = "3.7.0"
val avroVersion = "1.11.3"
val confluentVersion = "7.5.0"
val slf4jVersion = "2.0.9"
val logbackVersion = "1.4.14"

extra["kafkaVersion"] = kafkaVersion
extra["avroVersion"] = avroVersion
extra["confluentVersion"] = confluentVersion
extra["slf4jVersion"] = slf4jVersion
extra["logbackVersion"] = logbackVersion
