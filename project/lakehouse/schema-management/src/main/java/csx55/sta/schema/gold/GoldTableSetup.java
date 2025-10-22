package csx55.sta.schema.gold;

import csx55.sta.schema.AbstractTableSetup;
import csx55.sta.streaming.config.StreamConfig;

public class GoldTableSetup extends AbstractTableSetup {

  public GoldTableSetup(StreamConfig config) {
    super(config);
  }

  @Override
  protected String getLayerName() {
    return "Gold Layer";
  }

  @Override
  protected String getDDLResourcePath() {
    return "ddl/gold";
  }

  @Override
  protected void createNamespaces() {
    ensureNamespaceExists("lakehouse.gold");
  }
}
