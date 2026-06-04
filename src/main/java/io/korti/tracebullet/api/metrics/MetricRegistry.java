package io.korti.tracebullet.api.metrics;

/**
 * Registry that tracks active {@link Metric} instances and drives their periodic write schedule.
 * Metrics register themselves on {@link MetricEvent.RegisterMetricEvent} and unregister on
 * {@link MetricEvent.UnregisterMetricEvent}.
 */
public interface MetricRegistry {

	/**
	 * Adds the metric to the active set and schedules its {@link Metric#write()} calls.
	 */
	void register(Metric metric);

	/**
	 * Removes the metric from the active set and cancels its scheduled writes.
	 */
	void unregister(Metric metric);

}
