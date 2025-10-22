plugins {
  `java-library`
}

val slf4jVersion: String by rootProject.extra
val logbackVersion: String by rootProject.extra
val sparkVersion = "3.5.0"
val icebergVersion = "1.6.1"
val hadoopVersion = "3.3.4"
val typesafeConfigVersion = "1.4.3"

dependencies {
  api(rootProject.project(":schemas"))
  compileOnly("org.apache.spark:spark-sql_2.12:$sparkVersion")
  compileOnly("org.apache.spark:spark-streaming_2.12:$sparkVersion")
  compileOnly("org.apache.spark:spark-sql-kafka-0-10_2.12:$sparkVersion")
  compileOnly("org.apache.spark:spark-avro_2.12:$sparkVersion")
  compileOnly("za.co.absa:abris_2.12:6.4.0")
  compileOnly("org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:$icebergVersion")
  compileOnly("org.apache.hadoop:hadoop-aws:$hadoopVersion")
  compileOnly("com.amazonaws:aws-java-sdk-bundle:1.12.262")
  implementation("com.typesafe:config:$typesafeConfigVersion")

  api("org.slf4j:slf4j-api:$slf4jVersion")
  implementation("ch.qos.logback:logback-classic:$logbackVersion")
}
