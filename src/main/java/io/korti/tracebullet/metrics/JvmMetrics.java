package io.korti.tracebullet.metrics;

import io.korti.tracebullet.api.InstrumentationScopeNames;
import io.korti.tracebullet.api.metrics.BaseMetric;
import io.korti.tracebullet.api.metrics.MetricEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.Meter;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reports JVM health metrics for the server process.
 *
 * <p>Instruments (scope {@code minecraft.server}):
 * <ul>
 *   <li>{@code minecraft.server.jvm.memory.heap.used/committed/max} — heap memory gauges in bytes</li>
 *   <li>{@code minecraft.server.jvm.gc.collections} — GC collection count delta since last write, per GC pool</li>
 *   <li>{@code minecraft.server.jvm.gc.pause_time} — cumulative GC pause time delta since last write in ms, per GC pool</li>
 *   <li>{@code minecraft.server.jvm.cpu.load} — JVM process CPU load [0.0, 1.0]</li>
 *   <li>{@code minecraft.server.jvm.thread.count} — live JVM thread count</li>
 * </ul>
 * Attributes: {@code server.name}; GC instruments additionally carry {@code gc.name}.
 * GC counters report deltas since the previous write, not cumulative totals.
 */
public class JvmMetrics extends BaseMetric {

	private static final String HEAP_USED_METRIC_NAME = "minecraft.server.jvm.memory.heap.used";
	private static final String HEAP_USED_DESCRIPTION = "JVM heap memory currently used.";

	private static final String HEAP_COMMITTED_METRIC_NAME = "minecraft.server.jvm.memory.heap.committed";
	private static final String HEAP_COMMITTED_DESCRIPTION = "JVM heap memory committed to the JVM.";

	private static final String HEAP_MAX_METRIC_NAME = "minecraft.server.jvm.memory.heap.max";
	private static final String HEAP_MAX_DESCRIPTION = "Maximum JVM heap memory available.";

	private static final String HEAP_UNIT = "By";

	private static final String GC_COLLECTIONS_NAME = "minecraft.server.jvm.gc.collections";
	private static final String GC_COLLECTIONS_DESCRIPTION = "Number of garbage collections per GC pool since last observation.";
	private static final String GC_COLLECTIONS_UNIT = "{collection}";

	private static final String GC_PAUSE_TIME_NAME = "minecraft.server.jvm.gc.pause_time";
	private static final String GC_PAUSE_TIME_DESCRIPTION = "Cumulative GC pause time per GC pool since last observation.";
	private static final String GC_PAUSE_TIME_UNIT = "ms";

	private static final String CPU_LOAD_NAME = "minecraft.server.jvm.cpu.load";
	private static final String CPU_LOAD_DESCRIPTION = "JVM process CPU load as a ratio between 0.0 and 1.0.";
	private static final String CPU_LOAD_UNIT = "1";

	private static final String THREAD_COUNT_NAME = "minecraft.server.jvm.thread.count";
	private static final String THREAD_COUNT_DESCRIPTION = "Number of live JVM threads.";
	private static final String THREAD_COUNT_UNIT = "{thread}";

	private static final AttributeKey<String> SERVER_NAME = AttributeKey.stringKey("server.name");
	private static final AttributeKey<String> GC_NAME_KEY = AttributeKey.stringKey("gc.name");

	private final AtomicReference<LongGauge> heapUsedGauge = new AtomicReference<>();
	private final AtomicReference<LongGauge> heapCommittedGauge = new AtomicReference<>();
	private final AtomicReference<LongGauge> heapMaxGauge = new AtomicReference<>();

	private final AtomicReference<LongCounter> gcCollectionsCounter = new AtomicReference<>();
	private final AtomicReference<LongCounter> gcPauseTimeCounter = new AtomicReference<>();

	private final AtomicReference<DoubleGauge> cpuLoadGauge = new AtomicReference<>();
	private final AtomicReference<LongGauge> threadCountGauge = new AtomicReference<>();

	private final AtomicReference<MinecraftServer> serverRef = new AtomicReference<>();
	private final Map<String, GcState> gcPreviousState = new ConcurrentHashMap<>();

	@Override
	@SubscribeEvent
	public void register(MetricEvent.RegisterMetricEvent event) {
		super.register(event);
		Meter meter = event.getMeterProvider().get(InstrumentationScopeNames.SERVER);
		heapUsedGauge.set(meter.gaugeBuilder(HEAP_USED_METRIC_NAME)
				.ofLongs().setDescription(HEAP_USED_DESCRIPTION).setUnit(HEAP_UNIT).build());
		heapCommittedGauge.set(meter.gaugeBuilder(HEAP_COMMITTED_METRIC_NAME)
				.ofLongs().setDescription(HEAP_COMMITTED_DESCRIPTION).setUnit(HEAP_UNIT).build());
		heapMaxGauge.set(meter.gaugeBuilder(HEAP_MAX_METRIC_NAME)
				.ofLongs().setDescription(HEAP_MAX_DESCRIPTION).setUnit(HEAP_UNIT).build());
		gcCollectionsCounter.set(meter.counterBuilder(GC_COLLECTIONS_NAME)
				.setDescription(GC_COLLECTIONS_DESCRIPTION).setUnit(GC_COLLECTIONS_UNIT).build());
		gcPauseTimeCounter.set(meter.counterBuilder(GC_PAUSE_TIME_NAME)
				.setDescription(GC_PAUSE_TIME_DESCRIPTION).setUnit(GC_PAUSE_TIME_UNIT).build());
		cpuLoadGauge.set(meter.gaugeBuilder(CPU_LOAD_NAME)
				.setDescription(CPU_LOAD_DESCRIPTION).setUnit(CPU_LOAD_UNIT).build());
		threadCountGauge.set(meter.gaugeBuilder(THREAD_COUNT_NAME)
				.ofLongs().setDescription(THREAD_COUNT_DESCRIPTION).setUnit(THREAD_COUNT_UNIT).build());
	}

	@Override
	@SubscribeEvent
	public void unregister(MetricEvent.UnregisterMetricEvent event) {
		super.unregister(event);
		heapUsedGauge.set(null);
		heapCommittedGauge.set(null);
		heapMaxGauge.set(null);

		gcCollectionsCounter.set(null);
		gcPauseTimeCounter.set(null);

		cpuLoadGauge.set(null);
		threadCountGauge.set(null);

		gcPreviousState.clear();
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
		MinecraftServer server = serverRef.get();
		if (server == null) {
			return;
		}

		Attributes serverAttrs = attributesBuilder().put(SERVER_NAME, server.getWorldData().getLevelName()).build();
		writeMemory(serverAttrs);
		writeGc(serverAttrs);
		writeCpuAndThreads(serverAttrs);
	}

	private void writeMemory(Attributes serverAttrs) {
		LongGauge used = heapUsedGauge.get();
		LongGauge committed = heapCommittedGauge.get();
		LongGauge max = heapMaxGauge.get();
		if (used == null || committed == null || max == null) {
			return;
		}

		MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
		used.set(heap.getUsed(), serverAttrs);
		committed.set(heap.getCommitted(), serverAttrs);
		max.set(heap.getMax(), serverAttrs);
	}

	private void writeGc(Attributes serverAttrs) {
		LongCounter gcCollections = gcCollectionsCounter.get();
		LongCounter gcPauseTime = gcPauseTimeCounter.get();
		if (gcCollections == null || gcPauseTime == null) {
			return;
		}

		List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
		for (GarbageCollectorMXBean gc : gcBeans) {
			String name = gc.getName();

			long currentCount = gc.getCollectionCount();
			long currentTime = gc.getCollectionTime();

			GcState prev = gcPreviousState.computeIfAbsent(name, k -> new GcState());
			long deltaCount = currentCount - prev.count;
			long deltaTime = currentTime - prev.time;

			prev.count = currentCount;
			prev.time = currentTime;

			if (deltaCount > 0) {
				Attributes gcAttrs = Attributes.builder().putAll(serverAttrs).put(GC_NAME_KEY, name).build();
				gcCollections.add(deltaCount, gcAttrs);
				gcPauseTime.add(deltaTime, gcAttrs);
			}
		}
	}

	private void writeCpuAndThreads(Attributes serverAttrs) {
		DoubleGauge cpuLoad = cpuLoadGauge.get();
		LongGauge threadCount = threadCountGauge.get();
		if (cpuLoad == null || threadCount == null) {
			return;
		}

		double processCpuLoad = ((com.sun.management.OperatingSystemMXBean)
				ManagementFactory.getOperatingSystemMXBean()).getProcessCpuLoad();
		if (processCpuLoad >= 0) {
			cpuLoad.set(processCpuLoad, serverAttrs);
		}

		threadCount.set(ManagementFactory.getThreadMXBean().getThreadCount(), serverAttrs);
	}

	private static final class GcState {
		long count;
		long time;
	}
}
