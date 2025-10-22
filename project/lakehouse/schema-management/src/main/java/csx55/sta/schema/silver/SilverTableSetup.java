package csx55.sta.schema.silver;

import csx55.sta.schema.AbstractTableSetup;
import csx55.sta.streaming.config.StreamConfig;

public class SilverTableSetup extends AbstractTableSetup {

  public SilverTableSetup(StreamConfig config) {
    super(config);
  }

  @Override
  protected String getLayerName() {
    return "Silver Layer";
  }

  @Override
  protected String getDDLResourcePath() {
    return "ddl/silver";
  }

  @Override
  protected void createNamespaces() {
    ensureNamespaceExists("lakehouse.silver");
  }
}
