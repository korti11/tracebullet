package io.korti.tracebullet.metrics;

import io.korti.tracebullet.api.InstrumentationScopeNames;
import io.korti.tracebullet.api.metrics.Metric;
import io.korti.tracebullet.api.metrics.MetricEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.Meter;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicReference;

public class JvmMemoryMetric implements Metric {

	private static final String HEAP_USED_METRIC_NAME = "minecraft.server.jvm.memory.heap.used";
	private static final String HEAP_USED_DESCRIPTION = "JVM heap memory currently used.";

	private static final String HEAP_COMMITTED_METRIC_NAME = "minecraft.server.jvm.memory.heap.committed";
	private static final String HEAP_COMMITTED_DESCRIPTION = "JVM heap memory committed to the JVM.";

	private static final String HEAP_MAX_METRIC_NAME = "minecraft.server.jvm.memory.heap.max";
	private static final String HEAP_MAX_DESCRIPTION = "Maximum JVM heap memory available.";

	private static final String UNIT = "By";

	private static final AttributeKey<String> SERVER_NAME = AttributeKey.stringKey("server.name");

	private final AtomicReference<LongGauge> heapUsedGauge = new AtomicReference<>();
	private final AtomicReference<LongGauge> heapCommittedGauge = new AtomicReference<>();
	private final AtomicReference<LongGauge> heapMaxGauge = new AtomicReference<>();
	private final AtomicReference<MinecraftServer> serverRef = new AtomicReference<>();

	@Override
	@SubscribeEvent
	public void register(MetricEvent.RegisterMetricEvent event) {
		Metric.super.register(event);
		Meter meter = event.getMeterProvider().get(InstrumentationScopeNames.SERVER);
		heapUsedGauge.set(meter.gaugeBuilder(HEAP_USED_METRIC_NAME)
				.ofLongs().setDescription(HEAP_USED_DESCRIPTION).setUnit(UNIT).build());
		heapCommittedGauge.set(meter.gaugeBuilder(HEAP_COMMITTED_METRIC_NAME)
				.ofLongs().setDescription(HEAP_COMMITTED_DESCRIPTION).setUnit(UNIT).build());
		heapMaxGauge.set(meter.gaugeBuilder(HEAP_MAX_METRIC_NAME)
				.ofLongs().setDescription(HEAP_MAX_DESCRIPTION).setUnit(UNIT).build());
	}

	@Override
	@SubscribeEvent
	public void unregister(MetricEvent.UnregisterMetricEvent event) {
		Metric.super.unregister(event);
		heapUsedGauge.set(null);
		heapCommittedGauge.set(null);
		heapMaxGauge.set(null);
	}

	@SubscribeEvent
	public void onServerStart(ServerStartedEvent event) {
		serverRef.set(event.getServer());
	}

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		serverRef.set(null);
	}

	@Override
	public void write() {
		LongGauge used = heapUsedGauge.get();
		LongGauge committed = heapCommittedGauge.get();
		LongGauge max = heapMaxGauge.get();
		MinecraftServer server = serverRef.get();
		if (used == null || committed == null || max == null || server == null) {
			return;
		}

		MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
		Attributes attributes = Attributes.of(SERVER_NAME, server.getWorldData().getLevelName());
		used.set(heap.getUsed(), attributes);
		committed.set(heap.getCommitted(), attributes);
		max.set(heap.getMax(), attributes);
	}
}
