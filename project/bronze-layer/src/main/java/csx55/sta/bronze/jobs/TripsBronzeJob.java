package csx55.sta.bronze.jobs;

import csx55.sta.streaming.config.StreamConfig;

/**
 * Bronze layer ingestion job for Trip events
 * Reads trips.yellow topic and writes to lakehouse.bronze.trips
 */
public class TripsBronzeJob extends AbstractBronzeJob {

    public TripsBronzeJob(StreamConfig config) {
        super(config, config.getBronzeTripsConfig());
    }

    @Override
    protected String getJobName() {
        return "TripsBronzeJob";
    }

    public static void main(String[] args) throws Exception {
        StreamConfig config = new StreamConfig();
        TripsBronzeJob job = new TripsBronzeJob(config);
        job.run();
    }
}
