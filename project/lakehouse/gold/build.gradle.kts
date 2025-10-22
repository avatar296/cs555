plugins {
  java
  scala
  application
}

val slf4jVersion: String by rootProject.extra
val logbackVersion: String by rootProject.extra

val sparkVersion = "3.5.0"
val icebergVersion = "1.6.1"
val hadoopVersion = "3.3.4"
val scalaVersion = "2.12.18"
val typesafeConfigVersion = "1.4.3"

dependencies {
  implementation("org.scala-lang:scala-library:$scalaVersion")
  implementation(rootProject.project(":schemas"))
  implementation(project(":lakehouse:streaming"))
  implementation("com.typesafe:config:$typesafeConfigVersion")
  implementation("org.apache.spark:spark-sql_2.12:$sparkVersion")
  implementation("org.apache.spark:spark-avro_2.12:$sparkVersion")
  implementation("org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:$icebergVersion")
  implementation("org.apache.hadoop:hadoop-aws:$hadoopVersion")
  implementation("com.amazonaws:aws-java-sdk-bundle:1.12.262")
  implementation("org.slf4j:slf4j-api:$slf4jVersion")
  implementation("ch.qos.logback:logback-classic:$logbackVersion")
}

application {
  mainClass.set("csx55.sta.gold.GoldLayerApp")
}

tasks.jar {
  archiveBaseName.set("gold-layer")
  archiveVersion.set("")

  isZip64 = true

  manifest {
    attributes["Main-Class"] = "csx55.sta.gold.GoldLayerApp"
  }

  dependsOn(":lakehouse:streaming:jar")

  from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
  }

  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
