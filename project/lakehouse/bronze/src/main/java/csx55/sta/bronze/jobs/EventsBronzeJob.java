package csx55.sta.bronze.jobs;

import csx55.sta.streaming.config.StreamConfig;

public class EventsBronzeJob extends AbstractBronzeJob {

  public EventsBronzeJob(StreamConfig config) {
    super(config, config.getBronzeEventsConfig());
  }

  @Override
  protected String getJobName() {
    return "EventsBronzeJob";
  }

  public static void main(String[] args) throws Exception {
    StreamConfig config = new StreamConfig();
    EventsBronzeJob job = new EventsBronzeJob(config);
    job.run();
  }
}
