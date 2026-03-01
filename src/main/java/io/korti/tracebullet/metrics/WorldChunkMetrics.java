package io.korti.tracebullet.metrics;

import io.korti.tracebullet.api.InstrumentationScopeNames;
import io.korti.tracebullet.api.metrics.Metric;
import io.korti.tracebullet.api.metrics.MetricEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class WorldChunkMetrics implements Metric {

	private static final int WRITING_PERIOD_MINUTE = 1;

	private static final String LOADED_CHUNKS_METRIC_NAME = "minecraft.server.world.chunks.loaded";
	private static final String LOADED_CHUNKS_DESCRIPTION = "Currently loaded chunks of the world.";

	private static final String FORCE_LOADED_CHUNKS_METRIC_NAME = "minecraft.server.world.chunks.force_loaded";
	private static final String FORCE_LOADED_CHUNKS_DESCRIPTION = "Currently force loaded chunks of the world.";

	private static final String UNIT = "count";

	private static final AttributeKey<String> SERVER_NAME = AttributeKey.stringKey("server.name");
	private static final AttributeKey<String> DIMENSION_NAME = AttributeKey.stringKey("dimension.name");

	private final AtomicReference<LongCounter> loadedChunksCounter = new AtomicReference<>();
	private final AtomicReference<LongCounter> forceLoadedChunksCounter = new AtomicReference<>();
	private final AtomicReference<WorldChunkCalculator> worldChunkCalculator = new AtomicReference<>();

	@Override
	@SubscribeEvent
	public void register(MetricEvent.RegisterMetricEvent event) {
		Metric.super.register(event);
		Meter meter = event.getMeterProvider().get(InstrumentationScopeNames.WORLD);
		loadedChunksCounter.set(
				meter.counterBuilder(LOADED_CHUNKS_METRIC_NAME)
						.setDescription(LOADED_CHUNKS_DESCRIPTION)
						.setUnit(UNIT)
						.build()
		);
		forceLoadedChunksCounter.set(
				meter.counterBuilder(FORCE_LOADED_CHUNKS_METRIC_NAME)
						.setDescription(FORCE_LOADED_CHUNKS_DESCRIPTION)
						.setUnit(UNIT)
						.build()
		);
	}

	@Override
	@SubscribeEvent
	public void unregister(MetricEvent.UnregisterMetricEvent event) {
		Metric.super.unregister(event);
		loadedChunksCounter.set(null);
	}

	@SubscribeEvent
	public void onServerStart(ServerStartedEvent event) {
		worldChunkCalculator.set(new WorldChunkCalculator(event.getServer()));
	}

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		worldChunkCalculator.set(null);
	}

	@Override
	public long getWritingPeriod() {
		return TimeUnit.MINUTES.toSeconds(WRITING_PERIOD_MINUTE);
	}

	@Override
	public void write() {
		LongCounter loadedChunks = loadedChunksCounter.get();
		LongCounter forceLoadedChunks = forceLoadedChunksCounter.get();
		WorldChunkCalculator calculator = worldChunkCalculator.get();
		if (loadedChunks == null || forceLoadedChunks == null || calculator == null) {
			return;
		}

		calculator.calculateAndWrite(loadedChunks, forceLoadedChunks);
	}

	private static class WorldChunkCalculator {

		private final MinecraftServer server;

		WorldChunkCalculator(MinecraftServer server) {
			this.server = server;
		}

		void calculateAndWrite(LongCounter loadedChunks, LongCounter forceLoadedChunks) {
			if (server == null) {
				return;
			}

			String serverName = server.getWorldData().getLevelName();
			Iterable<ServerLevel> levels = server.getAllLevels();
			for (ServerLevel level : levels) {
				String levelName = level.dimension().identifier().toString();

				Attributes attributes = Attributes.of(SERVER_NAME, serverName, DIMENSION_NAME, levelName);
				loadedChunks.add(level.getChunkSource().getLoadedChunksCount(), attributes);
				forceLoadedChunks.add(level.getChunkSource().getForceLoadedChunks().size(), attributes);
			}
		}
	}
}
