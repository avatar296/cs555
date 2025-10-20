package csx55.sta.bronze;

import csx55.sta.bronze.jobs.EventsBronzeJob;
import csx55.sta.bronze.jobs.TripsBronzeJob;
import csx55.sta.bronze.jobs.WeatherBronzeJob;
import csx55.sta.streaming.config.StreamConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for Bronze Layer ingestion
 * Can run individual jobs or all jobs based on command-line arguments
 */
public class BronzeLayerApp {
    private static final Logger logger = LoggerFactory.getLogger(BronzeLayerApp.class);

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            logger.error("No job name provided. Expected: trips, weather, or events");
            System.exit(1);
        }

        String jobName = args[0];
        StreamConfig config = new StreamConfig();

        logger.info("Starting Bronze Layer: {}", jobName);

        switch (jobName.toLowerCase()) {
            case "trips":
                new TripsBronzeJob(config).run();
                break;

            case "weather":
                new WeatherBronzeJob(config).run();
                break;

            case "events":
                new EventsBronzeJob(config).run();
                break;

            default:
                logger.error("Unknown job: {}. Valid jobs: trips, weather, events", jobName);
                System.exit(1);
        }
    }
}
