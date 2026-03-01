package io.korti.tracebullet.api;

/**
 * Common instrumentation scope names used to get meters or traces.<br>
 * Example:
 * <pre>
 * {@code
 * 	@Override
 * 	public void register(MetricEvent.RegisterMetricEvent event) {
 * 		Metric.super.register(event);
 * 		Meter meter = event.getMeterProvider().get(InstrumentationScopeNames.SERVER);
 * 	}
 * }
 * </pre>
 */
public class InstrumentationScopeNames {

	/**
	 * Instrumentation scope name for metrics around the server.
	 */
	public static final String SERVER = "server";

	/**
	 * Instrumentation scope name for metrics around the world.
	 */
	public static final String WORLD = "world";
}
