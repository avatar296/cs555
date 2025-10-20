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
        // Create monitoring namespace for quality metrics
        ensureNamespaceExists("lakehouse.monitoring");

        // Create quarantine namespace (tables created on-demand)
        ensureNamespaceExists("lakehouse.quarantine");
    }

    @Override
    protected void performAdditionalSetup() {
        // Document quarantine tables (created on-demand during streaming)
        documentQuarantineTables();
    }

    private void documentQuarantineTables() {
        logger.info("");
        logger.info("Quarantine Tables (On-Demand Creation)");
        logger.info("=======================================");
        logger.info("");
        logger.info("Quarantine tables will be created dynamically when first batch is rejected:");
        logger.info("  - lakehouse.quarantine.trips_quarantined");
        logger.info("  - lakehouse.quarantine.weather_quarantined");
        logger.info("  - lakehouse.quarantine.events_quarantined");
        logger.info("");
        logger.info("Schema: Silver table columns + metadata:");
        logger.info("  + quarantine_timestamp (timestamp of rejection)");
        logger.info("  + batch_id (streaming batch identifier)");
        logger.info("  + failure_reason (Deequ constraint violations)");
        logger.info("  + validation_status (Deequ validation status)");
        logger.info("");
        logger.info("✓ Quarantine namespace prepared for on-demand table creation");
    }
}
