package io.korti.tracebullet.api.metrics;

public interface MetricRegistry {

	void register(Metric metric);

	void unregister(Metric metric);

}
