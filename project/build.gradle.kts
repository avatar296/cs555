/*
 * Spatial Temporal Analysis (STA) Project
 * CS X55 Term Project - NYC Taxi Streaming Analytics
 */

plugins {
    java
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1" apply false
    id("com.diffplug.spotless") version "6.25.0" apply false
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
    apply(plugin = "com.diffplug.spotless")

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/*/java/**/*.java")
            googleJavaFormat("1.17.0")
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }

        if (plugins.hasPlugin("scala")) {
            scala {
                target("src/*/scala/**/*.scala")
                scalafmt("3.7.17").configFile("${rootProject.projectDir}/.scalafmt.conf")
                trimTrailingWhitespace()
                endWithNewline()
            }
        }

        format("misc") {
            target("**/*.gradle.kts", "**/*.md", "**/.gitignore")
            trimTrailingWhitespace()
            indentWithSpaces(2)
            endWithNewline()
        }

        format("sql") {
            target("**/*.sql")
            trimTrailingWhitespace()
            endWithNewline()
        }

        format("xml") {
            target("**/*.xml")
            trimTrailingWhitespace()
            indentWithSpaces(2)
            endWithNewline()
        }

        kotlinGradle {
            target("*.gradle.kts")
            ktlint("1.0.1")
        }
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
