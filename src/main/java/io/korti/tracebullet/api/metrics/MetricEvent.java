package io.korti.tracebullet.api.metrics;

import io.opentelemetry.api.GlobalOpenTelemetry;
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

		public RegisterMetricEvent(MetricRegistry registry) {
			super(registry);
			this.meterProvider = GlobalOpenTelemetry.getMeterProvider();
		}

		public MeterProvider getMeterProvider() {
			return meterProvider;
		}
	}

	public static class UnregisterMetricEvent extends MetricEvent {

		public UnregisterMetricEvent(MetricRegistry registry) {
			super(registry);
		}

	}
}
