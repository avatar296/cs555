package csx55.sta.producer.stream;

import csx55.sta.producer.config.SyntheticProducerConfig;
import csx55.sta.producer.generator.SyntheticTripGenerator;
import csx55.sta.producer.util.ErrorInjector;
import csx55.sta.schema.TripEvent;

public class TripStreamProducer extends EventStreamProducer<TripEvent> {

  private final SyntheticTripGenerator generator;

  public TripStreamProducer(
      SyntheticProducerConfig.StreamConfig config, SyntheticProducerConfig globalConfig) {
    super("Trip", config, globalConfig);
    this.generator =
        new SyntheticTripGenerator(globalConfig.useRealtime, globalConfig.timeProgressionSeconds);
  }

  @Override
  protected TripEvent generateEvent() {
    return generator.generateEvent();
  }

  @Override
  protected TripEvent injectError(TripEvent event) {
    return errorInjector.maybeInjectError(event);
  }

  @Override
  protected boolean isInvalid(TripEvent event) {
    return ErrorInjector.isInvalidEvent(event);
  }

  @Override
  protected String getPartitionKey(TripEvent event) {
    return String.valueOf(event.getPickupLocationId());
  }
}
