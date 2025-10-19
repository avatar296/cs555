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
            printUsage();
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
                logger.error("Unknown job: {}", jobName);
                printUsage();
                System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: BronzeLayerApp <job-name>");
        System.out.println();
        System.out.println("Available jobs:");
        System.out.println("  trips   - Ingest trip events from trips.yellow topic");
        System.out.println("  weather - Ingest weather events from weather.updates topic");
        System.out.println("  events  - Ingest special events from special.events topic");
    }
}
