package csx55.sta.schema.monitoring;

import csx55.sta.schema.AbstractTableSetup;
import csx55.sta.streaming.config.StreamConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MonitoringSetup extends AbstractTableSetup {

  private static final Logger logger = LoggerFactory.getLogger(MonitoringSetup.class);

  public MonitoringSetup(StreamConfig config) {
    super(config);
  }

  @Override
  protected String getLayerName() {
    return "Monitoring Infrastructure";
  }

  @Override
  protected String getDDLResourcePath() {
    return "ddl/monitoring";
  }

  @Override
  protected void createNamespaces() {
    ensureNamespaceExists("lakehouse.monitoring");
    ensureNamespaceExists("lakehouse.quarantine");
  }

  @Override
  protected void performAdditionalSetup() {
    logger.info("Quarantine namespace prepared for on-demand table creation");
  }
}
