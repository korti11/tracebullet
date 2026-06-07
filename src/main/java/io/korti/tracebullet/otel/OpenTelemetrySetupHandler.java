package io.korti.tracebullet.otel;

import io.korti.tracebullet.Config;
import io.korti.tracebullet.TraceBullet;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;

import java.util.List;


public class OpenTelemetrySetupHandler {

	private static final String AUTHORIZATION_HEADER = "Authorization";

	private OpenTelemetrySdk registeredSdk;

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void setup(ServerStartedEvent event) {
		String otelEnvironment = Config.OTEL_URL.get();
		if (Strings.isBlank(otelEnvironment)) {
			TraceBullet.LOGGER.info("No OTel URL configured skipping setup.");
			return;
		}

		TraceBullet.LOGGER.info("Setup OTel exporter.");
		String serviceNameOverride = Config.SERVICE_NAME_OVERRIDE.get();

		AttributesBuilder attributes = Attributes.builder();
		String serverName = Strings.isBlank(serviceNameOverride) ? event.getServer().getWorldData().getLevelName() : serviceNameOverride;
		attributes.put("service.name", serverName);

		List<? extends String> customAttributes = Config.CUSTOM_ATTRIBUTES.get();
		for (String customAttribute : customAttributes) {
			String[] split = customAttribute.split("=", 2);
			if (split.length == 2) {
				attributes.put(split[0], split[1]);
			}
		}

		String otelAuth = Config.OTEL_AUTH_TOKEN.get();

		OtlpHttpMetricExporterBuilder metricExporterBuilder = OtlpHttpMetricExporter.builder()
				.setEndpoint(otelEnvironment + "/v1/metrics")
				.setAggregationTemporalitySelector(AggregationTemporalitySelector.alwaysCumulative());
		if (StringUtils.isNotBlank(otelAuth)) {
			metricExporterBuilder.addHeader(AUTHORIZATION_HEADER, otelAuth);
		}

		OtlpHttpMetricExporter metricExporter = metricExporterBuilder.build();

		SdkMeterProvider meterProvider = SdkMeterProvider.builder()
				.setResource(Resource.getDefault().merge(Resource.create(attributes.build())))
				.registerMetricReader(PeriodicMetricReader.builder(metricExporter).build())
				.build();

		registeredSdk = OpenTelemetrySdk.builder()
				.setMeterProvider(meterProvider)
				.setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
				.buildAndRegisterGlobal();
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void shutdown(ServerStoppingEvent event) {
		TraceBullet.LOGGER.info("Closing OTel exporter.");
		if (registeredSdk != null) {
			registeredSdk.close();
			registeredSdk = null;

			// Note: Only really needed for
			GlobalOpenTelemetry.resetForTest();
		}
	}

}
