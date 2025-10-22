package csx55.sta.gold.dimensions

import csx55.sta.streaming.config.StreamConfig
import csx55.sta.streaming.factory.IcebergSessionBuilder
import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.spark.sql.functions._
import org.slf4j.LoggerFactory

import java.sql.Timestamp
import java.time.LocalDateTime

object TimeDimensionLoader {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private val TABLE_NAME = "lakehouse.gold.time_dim"

  def main(args: Array[String]): Unit = {
    val config = new StreamConfig()
    val spark = IcebergSessionBuilder.createSession("TimeDimensionLoader", config)

    try {
      loadTimeDimension(spark)
    } catch {
      case e: Exception =>
        logger.error("Failed to load time dimension", e)
        System.exit(1)
    } finally {
      spark.stop()
    }
  }

  private def loadTimeDimension(spark: SparkSession): Unit = {
    import spark.implicits._

    val startDate = LocalDateTime.now().minusYears(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
    val endDate = LocalDateTime.now().plusYears(2).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)

    val hours = generateHourlyTimestamps(startDate, endDate)

    val timeDimDF = hours.toDF("hour_timestamp")
      .withColumn("hour_of_day", hour($"hour_timestamp"))
      .withColumn("day_of_week", date_format($"hour_timestamp", "EEEE"))
      .withColumn("day_of_week_num", dayofweek($"hour_timestamp"))
      .withColumn("day_of_month", dayofmonth($"hour_timestamp"))
      .withColumn("month", month($"hour_timestamp"))
      .withColumn("month_name", date_format($"hour_timestamp", "MMMM"))
      .withColumn("quarter", quarter($"hour_timestamp"))
      .withColumn("year", year($"hour_timestamp"))
      .withColumn("is_weekend", dayofweek($"hour_timestamp").isin(1, 7))
      .withColumn("is_morning_rush", hour($"hour_timestamp").between(7, 9))
      .withColumn("is_evening_rush", hour($"hour_timestamp").between(16, 19))
      .withColumn("is_rush_hour", hour($"hour_timestamp").between(7, 9) || hour($"hour_timestamp").between(16, 19))
      .withColumn("date_key", date_format($"hour_timestamp", "yyyy-MM-dd"))

    timeDimDF.write
      .format("iceberg")
      .mode(SaveMode.Overwrite)
      .saveAsTable(TABLE_NAME)
  }

  private def generateHourlyTimestamps(start: LocalDateTime, end: LocalDateTime): Seq[Timestamp] = {
    var current = start
    val timestamps = scala.collection.mutable.ArrayBuffer[Timestamp]()

    while (current.isBefore(end)) {
      timestamps += Timestamp.valueOf(current)
      current = current.plusHours(1)
    }

    timestamps.toSeq
  }
}
