plugins {
  java
  application
}

val kafkaVersion: String by rootProject.extra
val confluentVersion: String by rootProject.extra
val slf4jVersion: String by rootProject.extra
val logbackVersion: String by rootProject.extra

dependencies {
  implementation(project(":schemas"))
  implementation("org.apache.kafka:kafka-clients:$kafkaVersion")
  implementation("io.confluent:kafka-avro-serializer:$confluentVersion")
  implementation("org.slf4j:slf4j-api:$slf4jVersion")
  implementation("ch.qos.logback:logback-classic:$logbackVersion")
}

application {
  mainClass.set("csx55.sta.producer.ProducerApp")
}

tasks.named<JavaExec>("run") {
  standardInput = System.`in`
}

tasks.register<Jar>("fatJar") {
  archiveClassifier.set("all")
  from(sourceSets.main.get().output)
  dependsOn(configurations.runtimeClasspath)
  from({
    configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
  })
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  manifest {
    attributes["Main-Class"] = "csx55.sta.producer.ProducerApp"
  }
}
