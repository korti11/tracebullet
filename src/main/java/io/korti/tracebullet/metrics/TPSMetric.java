package io.korti.tracebullet.metrics;

import com.google.common.math.Stats;
import io.korti.tracebullet.api.InstrumentationScopeNames;
import io.korti.tracebullet.api.metrics.BaseMetric;
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

public class TPSMetric extends BaseMetric {

	private static final String TPS_METRIC_NAME = "minecraft.server.tps";
	private static final String TPS_DESCRIPTION = "TPS of Minecraft Server.";
	private static final String TPS_UNIT = "tps";

	private static final String TICK_TIME_METRIC_NAME = "minecraft.server.tick_time";
	private static final String TICK_TIME_DESCRIPTION = "Mean tick time per dimension.";
	private static final String TICK_TIME_UNIT = "ms";

	private static final AttributeKey<String> SERVER_NAME = AttributeKey.stringKey("server.name");
	private static final AttributeKey<String> DIMENSION_NAME = AttributeKey.stringKey("dimension.name");

	private final AtomicReference<DoubleGauge> tpsGauge = new AtomicReference<>();
	private final AtomicReference<DoubleGauge> tickTimeGauge = new AtomicReference<>();
	private final AtomicReference<TPSCalculator> tpsCalculator = new AtomicReference<>();

	@Override
	@SubscribeEvent
	public void register(MetricEvent.RegisterMetricEvent event) {
		super.register(event);

		Meter meter = event.getMeterProvider().get(InstrumentationScopeNames.SERVER);
		tpsGauge.set(meter.gaugeBuilder(TPS_METRIC_NAME).setDescription(TPS_DESCRIPTION).setUnit(TPS_UNIT).build());
		tickTimeGauge.set(meter.gaugeBuilder(TICK_TIME_METRIC_NAME).setDescription(TICK_TIME_DESCRIPTION).setUnit(TICK_TIME_UNIT).build());
	}

	@Override
	@SubscribeEvent
	public void unregister(MetricEvent.UnregisterMetricEvent event) {
		super.unregister(event);
		tpsGauge.set(null);
		tickTimeGauge.set(null);
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
		DoubleGauge tickTime = tickTimeGauge.get();
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

		void calculateAndWrite(DoubleGauge tpsGauge, DoubleGauge tickTimeGauge, Attributes customAttributes) {
			if (server == null) {
				return;
			}

			String serverName = server.getWorldData().getLevelName();
			Iterable<ServerLevel> levels = server.getAllLevels();
			for (ServerLevel level : levels) {
				String levelName = level.dimension().identifier().toString();
				Attributes attributes = Attributes.builder().putAll(customAttributes).put(SERVER_NAME, serverName).put(DIMENSION_NAME, levelName).build();

				double tickTimeMs = calculateTickTimeMs(server, level);
				TickRateManager tickRateManager = level == null ? server.tickRateManager() : level.tickRateManager();
				double tps = TimeUtil.MILLISECONDS_PER_SECOND / Math.max(tickTimeMs, tickRateManager.millisecondsPerTick());

				tpsGauge.set(tps, attributes);
				tickTimeGauge.set(tickTimeMs, attributes);
			}
		}

		private double calculateTickTimeMs(MinecraftServer server, ServerLevel level) {
			long[] times;
			if (level == null) {
				times = server.getTickTimesNanos();
			} else {
				var dimensionTimes = server.getTickTime(level.dimension());
				times = dimensionTimes == null ? UNLOADED : dimensionTimes;
			}
			return Stats.meanOf(times) / TimeUtil.NANOSECONDS_PER_MILLISECOND;
		}
	}

}
