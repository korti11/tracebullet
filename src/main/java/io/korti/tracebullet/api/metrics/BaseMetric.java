package io.korti.tracebullet.api.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;

/**
 * Abstract base class for {@link Metric} implementations that handles the lifecycle of custom
 * OTel attributes configured via {@code otel.custom_attributes}.
 *
 * <p>Subclasses should use {@link #attributesBuilder()} to construct data point attributes,
 * which automatically includes the configured custom attributes alongside any metric-specific ones:
 *
 * <pre>{@code
 * Attributes attrs = attributesBuilder()
 *     .put(SERVER_NAME, serverName)
 *     .put(DIMENSION_NAME, dimensionName)
 *     .build();
 * }</pre>
 *
 * <p>When overriding {@link #register} or {@link #unregister}, always call {@code super} first
 * to ensure custom attributes are captured or cleared before the subclass logic runs.
 */
public abstract class BaseMetric implements Metric {

	private volatile Attributes customAttributes = Attributes.empty();

	/**
	 * Registers this metric with the registry and captures the configured custom attributes.
	 * Subclasses must call {@code super.register(event)} before accessing {@link #attributesBuilder()}.
	 */
	@Override
	public void register(MetricEvent.RegisterMetricEvent event) {
		Metric.super.register(event);
		customAttributes = event.getCustomAttributes();
	}

	/**
	 * Unregisters this metric from the registry and clears the captured custom attributes.
	 * Subclasses must call {@code super.unregister(event)}.
	 */
	@Override
	public void unregister(MetricEvent.UnregisterMetricEvent event) {
		Metric.super.unregister(event);
		customAttributes = Attributes.empty();
	}

	/**
	 * Returns a new {@link AttributesBuilder} pre-populated with the custom attributes configured
	 * via {@code otel.custom_attributes}. Each call returns a fresh builder instance; callers
	 * add metric-specific attributes and call {@link AttributesBuilder#build()} to produce the
	 * final {@link Attributes} for a data point.
	 */
	protected final AttributesBuilder attributesBuilder() {
		return Attributes.builder().putAll(customAttributes);
	}
}
