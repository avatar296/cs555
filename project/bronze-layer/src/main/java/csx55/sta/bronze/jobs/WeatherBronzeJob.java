package csx55.sta.bronze.jobs;

import csx55.sta.streaming.config.StreamConfig;

/**
 * Bronze layer ingestion job for Weather events
 * Reads weather.updates topic and writes to lakehouse.bronze.weather
 */
public class WeatherBronzeJob extends AbstractBronzeJob {

    public WeatherBronzeJob(StreamConfig config) {
        super(config, config.getBronzeWeatherConfig());
    }

    @Override
    protected String getJobName() {
        return "WeatherBronzeJob";
    }

    public static void main(String[] args) throws Exception {
        StreamConfig config = new StreamConfig();
        WeatherBronzeJob job = new WeatherBronzeJob(config);
        job.run();
    }
}
