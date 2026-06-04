package io.korti.tracebullet.api.metrics;

import io.korti.tracebullet.Config;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.MeterProvider;
import net.neoforged.bus.api.Event;

public abstract class MetricEvent extends Event {

	private final MetricRegistry registry;

	protected MetricEvent(MetricRegistry registry) {
		this.registry = registry;
	}

	public MetricRegistry getRegistry() {
		return registry;
	}

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

		public MeterProvider getMeterProvider() {
			return meterProvider;
		}

		public Attributes getCustomAttributes() {
			return customAttributes;
		}
	}

	public static class UnregisterMetricEvent extends MetricEvent {

		public UnregisterMetricEvent(MetricRegistry registry) {
			super(registry);
		}

	}
}
