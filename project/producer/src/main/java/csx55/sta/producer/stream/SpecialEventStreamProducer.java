package csx55.sta.producer.stream;

import csx55.sta.producer.config.SyntheticProducerConfig;
import csx55.sta.producer.generator.SyntheticSpecialEventGenerator;
import csx55.sta.producer.util.ErrorInjector;
import csx55.sta.schema.SpecialEvent;

public class SpecialEventStreamProducer extends EventStreamProducer<SpecialEvent> {

    private final SyntheticSpecialEventGenerator generator;

    public SpecialEventStreamProducer(SyntheticProducerConfig.StreamConfig config,
                                      SyntheticProducerConfig globalConfig) {
        super("Event", config, globalConfig);
        this.generator = new SyntheticSpecialEventGenerator(
                globalConfig.useRealtime,
                globalConfig.timeProgressionSeconds
        );
    }

    @Override
    protected SpecialEvent generateEvent() {
        return generator.generateEvent();
    }

    @Override
    protected SpecialEvent injectError(SpecialEvent event) {
        return errorInjector.maybeInjectError(event);
    }

    @Override
    protected boolean isInvalid(SpecialEvent event) {
        return ErrorInjector.isInvalidEvent(event);
    }

    @Override
    protected String getPartitionKey(SpecialEvent event) {
        return String.valueOf(event.getLocationId());
    }
}
