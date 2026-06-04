package io.korti.tracebullet.metrics;

import io.korti.tracebullet.api.InstrumentationScopeNames;
import io.korti.tracebullet.api.metrics.BaseMetric;
import io.korti.tracebullet.api.metrics.MetricEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.Meter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class WorldChunkMetrics extends BaseMetric {

	private static final int WRITING_PERIOD_MINUTE = 1;

	private static final String LOADED_CHUNKS_METRIC_NAME = "minecraft.server.world.chunks.loaded";
	private static final String LOADED_CHUNKS_DESCRIPTION = "Currently loaded chunks of the world.";

	private static final String FORCE_LOADED_CHUNKS_METRIC_NAME = "minecraft.server.world.chunks.force_loaded";
	private static final String FORCE_LOADED_CHUNKS_DESCRIPTION = "Currently force loaded chunks of the world.";

	private static final String CHUNK_LOAD_METRIC_NAME = "minecraft.server.world.chunk.load";
	private static final String CHUNK_LOAD_DESCRIPTION = "Number of chunks loaded per dimension.";

	private static final String CHUNK_UNLOAD_METRIC_NAME = "minecraft.server.world.chunk.unload";
	private static final String CHUNK_UNLOAD_DESCRIPTION = "Number of chunks unloaded per dimension.";

	private static final String CHUNK_UNIT = "{chunk}";

	private static final AttributeKey<String> SERVER_NAME = AttributeKey.stringKey("server.name");
	private static final AttributeKey<String> DIMENSION_NAME = AttributeKey.stringKey("dimension.name");

	private final AtomicReference<LongGauge> loadedChunksGauge = new AtomicReference<>();
	private final AtomicReference<LongGauge> forceLoadedChunksGauge = new AtomicReference<>();
	private final AtomicReference<LongCounter> chunkLoadCounter = new AtomicReference<>();
	private final AtomicReference<LongCounter> chunkUnloadCounter = new AtomicReference<>();

	private final AtomicReference<WorldChunkCalculator> worldChunkCalculator = new AtomicReference<>();

	@Override
	@SubscribeEvent
	public void register(MetricEvent.RegisterMetricEvent event) {
		super.register(event);
		Meter meter = event.getMeterProvider().get(InstrumentationScopeNames.WORLD);
		loadedChunksGauge.set(
				meter.gaugeBuilder(LOADED_CHUNKS_METRIC_NAME)
						.setDescription(LOADED_CHUNKS_DESCRIPTION)
						.setUnit(CHUNK_UNIT)
						.ofLongs()
						.build()
		);
		forceLoadedChunksGauge.set(
				meter.gaugeBuilder(FORCE_LOADED_CHUNKS_METRIC_NAME)
						.setDescription(FORCE_LOADED_CHUNKS_DESCRIPTION)
						.setUnit(CHUNK_UNIT)
						.ofLongs()
						.build()
		);
		chunkLoadCounter.set(
				meter.counterBuilder(CHUNK_LOAD_METRIC_NAME)
						.setDescription(CHUNK_LOAD_DESCRIPTION)
						.setUnit(CHUNK_UNIT)
						.build()
		);
		chunkUnloadCounter.set(
				meter.counterBuilder(CHUNK_UNLOAD_METRIC_NAME)
						.setDescription(CHUNK_UNLOAD_DESCRIPTION)
						.setUnit(CHUNK_UNIT)
						.build()
		);
	}

	@Override
	@SubscribeEvent
	public void unregister(MetricEvent.UnregisterMetricEvent event) {
		super.unregister(event);
		loadedChunksGauge.set(null);
		forceLoadedChunksGauge.set(null);
		chunkLoadCounter.set(null);
		chunkUnloadCounter.set(null);
	}

	@SubscribeEvent
	public void onServerStart(ServerStartedEvent event) {
		worldChunkCalculator.set(new WorldChunkCalculator(event.getServer()));
	}

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		worldChunkCalculator.set(null);
	}

	@SubscribeEvent
	public void onChunkLoad(ChunkEvent.Load event) {
		if (!(event.getLevel() instanceof ServerLevel level)) {
			return;
		}
		LongCounter counter = chunkLoadCounter.get();
		if (counter == null) {
			return;
		}

		Attributes attributes = attributesBuilder()
				.put(SERVER_NAME, level.getServer().getWorldData().getLevelName())
				.put(DIMENSION_NAME, level.dimension().identifier().toString())
				.build();
		counter.add(1, attributes);
	}

	@SubscribeEvent
	public void onChunkUnload(ChunkEvent.Unload event) {
		if (!(event.getLevel() instanceof ServerLevel level)) {
			return;
		}
		LongCounter counter = chunkUnloadCounter.get();
		if (counter == null) {
			return;
		}

		Attributes attributes = attributesBuilder()
				.put(SERVER_NAME, level.getServer().getWorldData().getLevelName())
				.put(DIMENSION_NAME, level.dimension().identifier().toString())
				.build();
		counter.add(1, attributes);
	}

	@Override
	public long getWritingPeriod() {
		return TimeUnit.MINUTES.toSeconds(WRITING_PERIOD_MINUTE);
	}

	@Override
	public void write() {
		LongGauge loadedChunks = loadedChunksGauge.get();
		LongGauge forceLoadedChunks = forceLoadedChunksGauge.get();
		WorldChunkCalculator calculator = worldChunkCalculator.get();
		if (loadedChunks == null || forceLoadedChunks == null || calculator == null) {
			return;
		}

		calculator.calculateAndWrite(loadedChunks, forceLoadedChunks, attributesBuilder().build());
	}

	private static class WorldChunkCalculator {

		private final MinecraftServer server;

		WorldChunkCalculator(MinecraftServer server) {
			this.server = server;
		}

		void calculateAndWrite(LongGauge loadedChunks, LongGauge forceLoadedChunks, Attributes customAttributes) {
			if (server == null) {
				return;
			}

			String serverName = server.getWorldData().getLevelName();
			Iterable<ServerLevel> levels = server.getAllLevels();
			for (ServerLevel level : levels) {
				String levelName = level.dimension().identifier().toString();

				Attributes attributes = Attributes.builder().putAll(customAttributes).put(SERVER_NAME, serverName).put(DIMENSION_NAME, levelName).build();
				loadedChunks.set(level.getChunkSource().getLoadedChunksCount(), attributes);
				forceLoadedChunks.set(level.getChunkSource().getForceLoadedChunks().size(), attributes);
			}
		}
	}
}
