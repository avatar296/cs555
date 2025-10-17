package csx55.sta.producer.stream;

import csx55.sta.producer.config.SyntheticProducerConfig;
import csx55.sta.producer.generator.SyntheticWeatherGenerator;
import csx55.sta.producer.util.ErrorInjector;
import csx55.sta.schema.WeatherEvent;

public class WeatherStreamProducer extends EventStreamProducer<WeatherEvent> {

    private final SyntheticWeatherGenerator generator;

    public WeatherStreamProducer(SyntheticProducerConfig.StreamConfig config,
                                 SyntheticProducerConfig globalConfig) {
        super("Weather", config, globalConfig);
        this.generator = new SyntheticWeatherGenerator(
                globalConfig.useRealtime,
                globalConfig.timeProgressionSeconds
        );
    }

    @Override
    protected WeatherEvent generateEvent() {
        return generator.generateEvent();
    }

    @Override
    protected WeatherEvent injectError(WeatherEvent event) {
        return errorInjector.maybeInjectError(event);
    }

    @Override
    protected boolean isInvalid(WeatherEvent event) {
        return ErrorInjector.isInvalidEvent(event);
    }

    @Override
    protected String getPartitionKey(WeatherEvent event) {
        return String.valueOf(event.getLocationId());
    }
}
