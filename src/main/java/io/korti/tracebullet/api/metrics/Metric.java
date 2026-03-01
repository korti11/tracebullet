package io.korti.tracebullet.api.metrics;

import net.neoforged.bus.api.SubscribeEvent;

public interface Metric {

	void write();

	default void register(MetricEvent.RegisterMetricEvent event) {
		event.getRegistry().register(this);
	}

	default void unregister(MetricEvent.UnregisterMetricEvent event) {
		event.getRegistry().unregister(this);
	}

}
