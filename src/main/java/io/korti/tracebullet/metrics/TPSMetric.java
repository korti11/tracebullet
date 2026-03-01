package io.korti.tracebullet.metrics;

import com.google.common.math.Stats;
import io.korti.tracebullet.api.InstrumentationScopeNames;
import io.korti.tracebullet.api.metrics.Metric;
import io.korti.tracebullet.api.metrics.MetricEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.Meter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.TickRateManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.concurrent.atomic.AtomicReference;

public class TPSMetric implements Metric {

	private static final String METRIC_NAME = "minecraft.server.tps";
	private static final String DESCRIPTION = "TPS of Minecraft Server.";
	private static final String UNIT = "tps";

	private static final AttributeKey<String> SERVER_NAME = AttributeKey.stringKey("server.name");
	private static final AttributeKey<String> DIMENSION_NAME = AttributeKey.stringKey("dimension.name");

	private final AtomicReference<DoubleGauge> tpsGauge = new AtomicReference<>();
	private final AtomicReference<TPSCalculator> tpsCalculator = new AtomicReference<>();

	@Override
	@SubscribeEvent
	public void register(MetricEvent.RegisterMetricEvent event) {
		Metric.super.register(event);

		Meter meter = event.getMeterProvider().get(InstrumentationScopeNames.SERVER);
		tpsGauge.set(meter.gaugeBuilder(METRIC_NAME).setDescription(DESCRIPTION).setUnit(UNIT).build());
	}

	@Override
	@SubscribeEvent
	public void unregister(MetricEvent.UnregisterMetricEvent event) {
		Metric.super.unregister(event);
		tpsGauge.set(null);
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
		DoubleGauge gauge = tpsGauge.get();
		TPSCalculator calculator = tpsCalculator.get();
		if (gauge == null || calculator == null) {
			return;
		}

		calculator.calculateAndWrite(gauge);
	}

	private static class TPSCalculator {

		private static final long[] UNLOADED = new long[] { 0 };

		private final MinecraftServer server;

		TPSCalculator(MinecraftServer server) {
			this.server = server;
		}

		void calculateAndWrite(DoubleGauge gauge) {
			if (server == null) {
				return;
			}

			String serverName = server.getWorldData().getLevelName();
			Iterable<ServerLevel> levels = server.getAllLevels();
			for (ServerLevel level : levels) {
				String levelName = level.dimension().identifier().toString();

				gauge.set(calculateTps(server, level), Attributes.of(SERVER_NAME, serverName, DIMENSION_NAME, levelName));
			}
		}

		private double calculateTps(MinecraftServer server, ServerLevel level) {
			long[] times;
			TickRateManager tickRateManager;

			if (level == null) {
				times = server.getTickTimesNanos();
				tickRateManager = server.tickRateManager();
			} else {
				var dimensionTimes = server.getTickTime(level.dimension());
				times = dimensionTimes == null ? UNLOADED : dimensionTimes;
				tickRateManager = level.tickRateManager();
			}

			double tickTime = Stats.meanOf(times) / TimeUtil.NANOSECONDS_PER_MILLISECOND;
			return TimeUtil.MILLISECONDS_PER_SECOND / Math.max(tickTime, tickRateManager.millisecondsPerTick());
		}
	}

}
