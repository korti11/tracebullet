package io.korti.tracebullet.api.metrics;

/**
 * Core contract for all TraceBullet metrics.
 *
 * <p>Implementations are NeoForge event subscribers. They subscribe to
 * {@link MetricEvent.RegisterMetricEvent} and {@link MetricEvent.UnregisterMetricEvent} on the
 * game event bus to acquire and release their OTel instruments, and are called periodically by
 * the scheduler via {@link #write()}.
 *
 * <p>The preferred base for new metrics is {@link BaseMetric}, which handles custom attribute
 * propagation automatically. Only implement this interface directly when inheriting from
 * {@code BaseMetric} is not possible.
 */
public interface Metric {

	int DEFAULT_WRITING_PERIOD_SECONDS = 10;

	/**
	 * Writes the current metric values to the OTel instruments.
	 * Called on the metric thread pool at the interval returned by {@link #getWritingPeriod()}.
	 */
	void write();

	/**
	 * Returns the interval between consecutive {@link #write()} calls, in seconds.
	 * Defaults to {@value DEFAULT_WRITING_PERIOD_SECONDS} seconds.
	 */
	default long getWritingPeriod() {
		return DEFAULT_WRITING_PERIOD_SECONDS;
	}

	/**
	 * Registers this metric with the registry so the scheduler can call {@link #write()}.
	 * Implementations must call this default (via {@code Metric.super.register(event)} or
	 * {@code super.register(event)}) before creating OTel instruments.
	 */
	default void register(MetricEvent.RegisterMetricEvent event) {
		event.getRegistry().register(this);
	}

	/**
	 * Unregisters this metric from the registry and stops scheduled writes.
	 * Implementations must call this default and null out any instrument references afterward.
	 */
	default void unregister(MetricEvent.UnregisterMetricEvent event) {
		event.getRegistry().unregister(this);
	}

}
