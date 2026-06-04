package io.korti.tracebullet.metrics;

import com.google.common.math.Stats;
import io.korti.tracebullet.api.InstrumentationScopeNames;
import io.korti.tracebullet.api.metrics.BaseMetric;
import io.korti.tracebullet.api.metrics.MetricEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.TickRateManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reports server TPS and mean tick time, broken down per dimension.
 *
 * <p>Instruments (scope {@code minecraft.server}):
 * <ul>
 *   <li>{@code minecraft.server.tps} — ticks per second gauge per dimension</li>
 *   <li>{@code minecraft.server.tick_time} — tick duration histogram in ms per dimension</li>
 * </ul>
 * Attributes: {@code server.name}, {@code dimension.name}.
 */
public class TPSMetric extends BaseMetric {

	private static final String TPS_METRIC_NAME = "minecraft.server.tps";
	private static final String TPS_DESCRIPTION = "TPS of Minecraft Server.";
	private static final String TPS_UNIT = "tps";

	private static final String TICK_TIME_METRIC_NAME = "minecraft.server.tick_time";
	private static final String TICK_TIME_DESCRIPTION = "Tick time distribution per dimension.";
	private static final String TICK_TIME_UNIT = "ms";

	private static final List<Double> TICK_TIME_BUCKETS = List.of(5.0, 10.0, 20.0, 30.0, 40.0, 50.0, 75.0, 100.0, 150.0, 200.0, 300.0, 500.0);

	private static final AttributeKey<String> SERVER_NAME = AttributeKey.stringKey("server.name");
	private static final AttributeKey<String> DIMENSION_NAME = AttributeKey.stringKey("dimension.name");

	private final AtomicReference<DoubleGauge> tpsGauge = new AtomicReference<>();
	private final AtomicReference<DoubleHistogram> tickTimeHistogram = new AtomicReference<>();
	private final AtomicReference<TPSCalculator> tpsCalculator = new AtomicReference<>();

	@Override
	@SubscribeEvent
	public void register(MetricEvent.RegisterMetricEvent event) {
		super.register(event);

		Meter meter = event.getMeterProvider().get(InstrumentationScopeNames.SERVER);
		tpsGauge.set(meter.gaugeBuilder(TPS_METRIC_NAME).setDescription(TPS_DESCRIPTION).setUnit(TPS_UNIT).build());
		tickTimeHistogram.set(meter.histogramBuilder(TICK_TIME_METRIC_NAME)
				.setDescription(TICK_TIME_DESCRIPTION)
				.setUnit(TICK_TIME_UNIT)
				.setExplicitBucketBoundariesAdvice(TICK_TIME_BUCKETS)
				.build());
	}

	@Override
	@SubscribeEvent
	public void unregister(MetricEvent.UnregisterMetricEvent event) {
		super.unregister(event);
		tpsGauge.set(null);
		tickTimeHistogram.set(null);
	}

	@SubscribeEvent
	public void onServerStart(ServerStartedEvent event) {
		tpsCalculator.set(new TPSCalculator(event.getServer()));
	}

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		tpsCalculator.set(null);
	}

	@Override
	public void write() {
		DoubleGauge tps = tpsGauge.get();
		DoubleHistogram tickTime = tickTimeHistogram.get();
		TPSCalculator calculator = tpsCalculator.get();
		if (tps == null || tickTime == null || calculator == null) {
			return;
		}

		calculator.calculateAndWrite(tps, tickTime, attributesBuilder().build());
	}

	private static class TPSCalculator {

		private static final long[] UNLOADED = new long[] { 0 };

		private final MinecraftServer server;

		TPSCalculator(MinecraftServer server) {
			this.server = server;
		}

		void calculateAndWrite(DoubleGauge tpsGauge, DoubleHistogram tickTimeHistogram, Attributes customAttributes) {
			if (server == null) {
				return;
			}

			String serverName = server.getWorldData().getLevelName();
			Iterable<ServerLevel> levels = server.getAllLevels();
			for (ServerLevel level : levels) {
				String levelName = level.dimension().identifier().toString();
				Attributes attributes = Attributes.builder().putAll(customAttributes).put(SERVER_NAME, serverName).put(DIMENSION_NAME, levelName).build();

				long[] rawTimes = getRawTickTimes(server, level);
				double meanTickTimeMs = Stats.meanOf(rawTimes) / TimeUtil.NANOSECONDS_PER_MILLISECOND;
				TickRateManager tickRateManager = level == null ? server.tickRateManager() : level.tickRateManager();
				double tps = TimeUtil.MILLISECONDS_PER_SECOND / Math.max(meanTickTimeMs, tickRateManager.millisecondsPerTick());

				tpsGauge.set(tps, attributes);
				for (long timeNs : rawTimes) {
					tickTimeHistogram.record(timeNs / (double) TimeUtil.NANOSECONDS_PER_MILLISECOND, attributes);
				}
			}
		}

		private long[] getRawTickTimes(MinecraftServer server, ServerLevel level) {
			if (level == null) {
				return server.getTickTimesNanos();
			}
			var dimensionTimes = server.getTickTime(level.dimension());
			return dimensionTimes == null ? UNLOADED : dimensionTimes;
		}
	}

}
