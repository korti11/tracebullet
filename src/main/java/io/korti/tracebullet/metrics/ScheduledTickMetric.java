package io.korti.tracebullet.metrics;

import io.korti.tracebullet.api.InstrumentationScopeNames;
import io.korti.tracebullet.api.metrics.BaseMetric;
import io.korti.tracebullet.api.metrics.MetricEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.Meter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reports the pending scheduled-tick queue size per dimension, sampled every minute.
 * A growing queue is a leading indicator of server lag before TPS visibly drops.
 *
 * <p>Instruments (scope {@code minecraft.world}):
 * <ul>
 *   <li>{@code minecraft.server.world.scheduled_ticks.block} — pending block tick queue size</li>
 *   <li>{@code minecraft.server.world.scheduled_ticks.fluid} — pending fluid tick queue size</li>
 * </ul>
 * Attributes: {@code server.name}, {@code dimension.name}.
 */
public class ScheduledTickMetric extends BaseMetric {

	private static final int WRITING_PERIOD_MINUTE = 1;

	private static final String BLOCK_TICKS_METRIC_NAME = "minecraft.server.world.scheduled_ticks.block";
	private static final String BLOCK_TICKS_DESCRIPTION = "Pending scheduled block tick queue size per dimension.";

	private static final String FLUID_TICKS_METRIC_NAME = "minecraft.server.world.scheduled_ticks.fluid";
	private static final String FLUID_TICKS_DESCRIPTION = "Pending scheduled fluid tick queue size per dimension.";

	private static final String TICK_UNIT = "{tick}";

	private static final AttributeKey<String> SERVER_NAME = AttributeKey.stringKey("server.name");
	private static final AttributeKey<String> DIMENSION_NAME = AttributeKey.stringKey("dimension.name");

	private final AtomicReference<LongGauge> blockTicksGauge = new AtomicReference<>();
	private final AtomicReference<LongGauge> fluidTicksGauge = new AtomicReference<>();

	private final AtomicReference<ScheduledTickCalculator> scheduledTickCalculator = new AtomicReference<>();

	@Override
	@SubscribeEvent
	public void register(MetricEvent.RegisterMetricEvent event) {
		super.register(event);
		Meter meter = event.getMeterProvider().get(InstrumentationScopeNames.WORLD);
		blockTicksGauge.set(
				meter.gaugeBuilder(BLOCK_TICKS_METRIC_NAME)
						.setDescription(BLOCK_TICKS_DESCRIPTION)
						.setUnit(TICK_UNIT)
						.ofLongs()
						.build()
		);
		fluidTicksGauge.set(
				meter.gaugeBuilder(FLUID_TICKS_METRIC_NAME)
						.setDescription(FLUID_TICKS_DESCRIPTION)
						.setUnit(TICK_UNIT)
						.ofLongs()
						.build()
		);
	}

	@Override
	@SubscribeEvent
	public void unregister(MetricEvent.UnregisterMetricEvent event) {
		super.unregister(event);
		blockTicksGauge.set(null);
		fluidTicksGauge.set(null);
	}

	@SubscribeEvent
	public void onServerStart(ServerStartedEvent event) {
		scheduledTickCalculator.set(new ScheduledTickCalculator(event.getServer()));
	}

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		scheduledTickCalculator.set(null);
	}

	@Override
	public long getWritingPeriod() {
		return TimeUnit.MINUTES.toSeconds(WRITING_PERIOD_MINUTE);
	}

	@Override
	public void write() {
		LongGauge blockTicks = blockTicksGauge.get();
		LongGauge fluidTicks = fluidTicksGauge.get();
		ScheduledTickCalculator calculator = scheduledTickCalculator.get();
		if (blockTicks == null || fluidTicks == null || calculator == null) {
			return;
		}

		calculator.calculateAndWrite(blockTicks, fluidTicks, attributesBuilder().build());
	}

	private static class ScheduledTickCalculator {

		private final MinecraftServer server;

		ScheduledTickCalculator(MinecraftServer server) {
			this.server = server;
		}

		void calculateAndWrite(LongGauge blockTicks, LongGauge fluidTicks, Attributes customAttributes) {
			if (server == null) {
				return;
			}

			String serverName = server.getWorldData().getLevelName();
			Iterable<ServerLevel> levels = server.getAllLevels();
			for (ServerLevel level : levels) {
				String levelName = level.dimension().identifier().toString();

				int blockCount = level.getBlockTicks().count();
				int fluidCount = level.getFluidTicks().count();
				if (blockCount == 0 && fluidCount == 0) {
					continue;
				}

				Attributes attributes = Attributes.builder().putAll(customAttributes).put(SERVER_NAME, serverName).put(DIMENSION_NAME, levelName).build();
				if (blockCount > 0) {
					blockTicks.set(blockCount, attributes);
				}
				if (fluidCount > 0) {
					fluidTicks.set(fluidCount, attributes);
				}
			}
		}
	}
}
