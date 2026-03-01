package io.korti.tracebullet.api.metrics;

public interface Metric {

	int DEFAULT_WRITING_PERIOD_SECONDS = 10;

	void write();

	/**
	 * @return the period between two writes in seconds.
	 */
	default long getWritingPeriod() {
		return DEFAULT_WRITING_PERIOD_SECONDS;
	}

	default void register(MetricEvent.RegisterMetricEvent event) {
		event.getRegistry().register(this);
	}

	default void unregister(MetricEvent.UnregisterMetricEvent event) {
		event.getRegistry().unregister(this);
	}

}
