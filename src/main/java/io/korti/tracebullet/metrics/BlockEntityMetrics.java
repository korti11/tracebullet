package io.korti.tracebullet.metrics;

import io.korti.tracebullet.api.InstrumentationScopeNames;
import io.korti.tracebullet.api.metrics.Metric;
import io.korti.tracebullet.api.metrics.MetricEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BlockEntityMetrics implements Metric {

	private static final int WRITING_PERIOD_MINUTE = 1;

	private static final String METRIC_NAME = "minecraft.server.world.block_entity";
	private static final String DESCRIPTION = "Amount of block entities per chunk in the world.";
	private static final String UNIT = "count";

	private static final AttributeKey<String> SERVER_NAME = AttributeKey.stringKey("server.name");
	private static final AttributeKey<String> DIMENSION_NAME = AttributeKey.stringKey("dimension.name");
	private static final AttributeKey<String> BLOCK_ENTITY_TYPE = AttributeKey.stringKey("block_entity.type");
	private static final AttributeKey<Long> CHUNK_POS_X = AttributeKey.longKey("chunk.pos.x");
	private static final AttributeKey<Long> CHUNK_POS_Z = AttributeKey.longKey("chunk.pos.z");

	private final AtomicReference<LongCounter> blockEntityCounter = new AtomicReference<>();
	private final AtomicReference<BlockEntityCalculator> blockEntityCalculator = new AtomicReference<>();

	@Override
	@SubscribeEvent
	public void register(MetricEvent.RegisterMetricEvent event) {
		Metric.super.register(event);
		Meter meter = event.getMeterProvider().get(InstrumentationScopeNames.WORLD);
		blockEntityCounter.set(
				meter.counterBuilder(METRIC_NAME)
						.setDescription(DESCRIPTION)
						.setUnit(UNIT)
						.build()
		);
	}

	@Override
	@SubscribeEvent
	public void unregister(MetricEvent.UnregisterMetricEvent event) {
		Metric.super.unregister(event);
		blockEntityCounter.set(null);
	}

	@SubscribeEvent
	public void onServerStart(ServerStartedEvent event) {
		blockEntityCalculator.set(new BlockEntityCalculator(event.getServer()));
	}

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		blockEntityCalculator.set(null);
	}

	@Override
	public long getWritingPeriod() {
		return TimeUnit.MINUTES.toSeconds(WRITING_PERIOD_MINUTE);
	}

	@Override
	public void write() {
		LongCounter counter = blockEntityCounter.get();
		BlockEntityCalculator calculator = blockEntityCalculator.get();
		if (counter == null || calculator == null) {
			return;
		}

		calculator.calculateAndWrite(counter);
	}

	private static class BlockEntityCalculator {

		private final MinecraftServer server;

		BlockEntityCalculator(MinecraftServer server) {
			this.server = server;
		}

		void calculateAndWrite(LongCounter counter) {
			if (server == null) {
				return;
			}

			String serverName = server.getWorldData().getLevelName();
			for (ServerLevel level : server.getAllLevels()) {
				String levelName = level.dimension().identifier().toString();
				Attributes baseAttributes = Attributes.of(SERVER_NAME, serverName, DIMENSION_NAME, levelName);

				Map<BlockEntityKey, Counter> counts = new HashMap<>();
				level.getChunkSource().chunkMap.forEachReadyToSendChunk(chunk -> {
					ChunkPos chunkPos = chunk.getPos();
					for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
						Identifier typeKey = BlockEntityType.getKey(blockEntity.getType());
						if (typeKey == null) {
							continue;
						}
						counts.computeIfAbsent(new BlockEntityKey(typeKey, chunkPos), k -> new Counter()).value++;
					}
				});

				for (Map.Entry<BlockEntityKey, Counter> entry : counts.entrySet()) {
					BlockEntityKey key = entry.getKey();
					long chunkLong = key.chunkPos().toLong();
					int x = ChunkPos.getX(chunkLong);
					int z = ChunkPos.getZ(chunkLong);

					AttributesBuilder builder = Attributes.builder()
							.putAll(baseAttributes)
							.put(BLOCK_ENTITY_TYPE, key.typeKey().toString())
							.put(CHUNK_POS_X, (long) x)
							.put(CHUNK_POS_Z, (long) z);

					counter.add(entry.getValue().value, builder.build());
				}
			}
		}

		private static class Counter {
			long value = 0;
		}
	}

	private record BlockEntityKey(Identifier typeKey, ChunkPos chunkPos) {

	}
}
