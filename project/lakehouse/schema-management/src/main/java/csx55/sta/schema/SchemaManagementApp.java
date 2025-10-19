package csx55.sta.schema;

import csx55.sta.schema.bronze.BronzeTableSetup;
import csx55.sta.schema.silver.SilverTableSetup;
import csx55.sta.streaming.config.StreamConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schema Management Application
 *
 * Centralized entry point for creating and managing Iceberg table schemas
 * across all medallion layers (Bronze, Silver, Gold).
 *
 * This separates schema management from data processing pipelines.
 */
public class SchemaManagementApp {

    private static final Logger logger = LoggerFactory.getLogger(SchemaManagementApp.class);

    public static void main(String[] args) {
        if (args.length == 0) {
            logger.error("Usage: SchemaManagementApp <layer>");
            logger.error("  layer: bronze | silver | gold | all");
            System.exit(1);
        }

        String layer = args[0].toLowerCase();

        try {
            logger.info("========================================");
            logger.info("Schema Management - Layer: {}", layer);
            logger.info("========================================");

            StreamConfig config = new StreamConfig();

            switch (layer) {
                case "bronze":
                    setupBronze(config);
                    break;

                case "silver":
                    setupSilver(config);
                    break;

                case "gold":
                    logger.warn("Gold layer setup not yet implemented");
                    break;

                case "all":
                    setupBronze(config);
                    setupSilver(config);
                    logger.warn("Gold layer setup not yet implemented");
                    break;

                default:
                    logger.error("Unknown layer: {}. Valid options: bronze, silver, gold, all", layer);
                    System.exit(1);
            }

            logger.info("========================================");
            logger.info("Schema management complete!");
            logger.info("========================================");
            System.exit(0);

        } catch (Exception e) {
            logger.error("Schema management failed for layer: {}", layer, e);
            System.exit(1);
        }
    }

    private static void setupBronze(StreamConfig config) {
        logger.info("Setting up Bronze layer tables...");
        BronzeTableSetup setup = new BronzeTableSetup(config);
        setup.run();
    }

    private static void setupSilver(StreamConfig config) {
        logger.info("Setting up Silver layer tables...");
        SilverTableSetup setup = new SilverTableSetup(config);
        setup.run();
    }
}
