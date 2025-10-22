package csx55.sta.schema;

import csx55.sta.schema.bronze.BronzeTableSetup;
import csx55.sta.schema.gold.GoldTableSetup;
import csx55.sta.schema.monitoring.MonitoringSetup;
import csx55.sta.schema.silver.SilverTableSetup;
import csx55.sta.streaming.config.StreamConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaManagementApp {

  private static final Logger logger = LoggerFactory.getLogger(SchemaManagementApp.class);

  public static void main(String[] args) {
    if (args.length == 0) {
      logger.error("Usage: SchemaManagementApp <layer>");
      logger.error("  layer: bronze | silver | gold | monitoring | all");
      System.exit(1);
    }

    String layer = args[0].toLowerCase();

    try {
      logger.info("Schema Management - Layer: {}", layer);
      StreamConfig config = new StreamConfig();

      switch (layer) {
        case "bronze":
          setupBronze(config);
          break;

        case "silver":
          setupSilver(config);
          break;

        case "gold":
          setupGold(config);
          break;

        case "monitoring":
          setupMonitoring(config);
          break;

        case "all":
          setupBronze(config);
          setupSilver(config);
          setupGold(config);
          setupMonitoring(config);
          break;

        default:
          logger.error(
              "Unknown layer: {}. Valid options: bronze, silver, gold, monitoring, all", layer);
          System.exit(1);
      }

      logger.info("Schema management complete");
      System.exit(0);

    } catch (Exception e) {
      logger.error("Schema management failed for layer: {}", layer, e);
      System.exit(1);
    }
  }

  private static void setupBronze(StreamConfig config) {
    new BronzeTableSetup(config).run();
  }

  private static void setupSilver(StreamConfig config) {
    new SilverTableSetup(config).run();
  }

  private static void setupGold(StreamConfig config) {
    new GoldTableSetup(config).run();
  }

  private static void setupMonitoring(StreamConfig config) {
    new MonitoringSetup(config).run();
  }
}
