package io.korti.tracebullet.api.metrics;

import io.korti.tracebullet.Config;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.MeterProvider;
import net.neoforged.bus.api.Event;

/**
 * Base class for events that drive the metric lifecycle on the NeoForge game event bus.
 *
 * @see RegisterMetricEvent
 * @see UnregisterMetricEvent
 */
public abstract class MetricEvent extends Event {

	private final MetricRegistry registry;

	protected MetricEvent(MetricRegistry registry) {
		this.registry = registry;
	}

	public MetricRegistry getRegistry() {
		return registry;
	}

	/**
	 * Fired when the server has started and metrics should initialize their OTel instruments.
	 * Subscribers receive the global {@link MeterProvider} and the parsed custom attributes from
	 * {@code otel.custom_attributes} config. Posted at {@code EventPriority.LOWEST} so the OTel
	 * SDK (registered at {@code HIGHEST}) is guaranteed to be initialized first.
	 */
	public static class RegisterMetricEvent extends MetricEvent {

		private final MeterProvider meterProvider;
		private final Attributes customAttributes;

		public RegisterMetricEvent(MetricRegistry registry) {
			super(registry);
			this.meterProvider = GlobalOpenTelemetry.getMeterProvider();
			AttributesBuilder builder = Attributes.builder();
			for (String attr : Config.CUSTOM_ATTRIBUTES.get()) {
				String[] parts = attr.split("=", 2);
				if (parts.length == 2) builder.put(parts[0], parts[1]);
			}
			this.customAttributes = builder.build();
		}

		/** Returns the global {@link MeterProvider} to use when creating OTel instruments. */
		public MeterProvider getMeterProvider() {
			return meterProvider;
		}

		/**
		 * Returns the custom data point attributes parsed from {@code otel.custom_attributes}.
		 * These are passed to {@link BaseMetric} automatically; direct {@link Metric} implementors
		 * must handle them manually if needed.
		 */
		public Attributes getCustomAttributes() {
			return customAttributes;
		}
	}

	/**
	 * Fired when the server is stopping and metrics should release their OTel instruments.
	 * Posted at {@code EventPriority.LOWEST} so metrics clean up before the OTel SDK shuts down.
	 */
	public static class UnregisterMetricEvent extends MetricEvent {

		public UnregisterMetricEvent(MetricRegistry registry) {
			super(registry);
		}

	}
}
