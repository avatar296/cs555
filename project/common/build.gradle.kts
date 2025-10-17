plugins {
    java
    id("com.github.davidmc24.gradle.plugin.avro")
}

val avroVersion: String by rootProject.extra
val slf4jVersion: String by rootProject.extra

dependencies {
    // Avro for schema management
    implementation("org.apache.avro:avro:$avroVersion")

    // Logging
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}

// Configure Avro plugin
avro {
    setCreateSetters(false)
    setFieldVisibility("PRIVATE")
    setOutputCharacterEncoding("UTF-8")
}
