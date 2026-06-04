package io.korti.tracebullet.metrics;

import io.korti.tracebullet.api.InstrumentationScopeNames;
import io.korti.tracebullet.api.metrics.Metric;
import io.korti.tracebullet.api.metrics.MetricEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.Meter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ItemEntityMetrics implements Metric {

	private static final int WRITING_PERIOD_MINUTE = 1;

	private static final String METRIC_NAME = "minecraft.server.world.entity.item";
	private static final String DESCRIPTION = "Amount of item entities per chunk in the world.";
	private static final String UNIT = "{entity}";

	private static final AttributeKey<String> SERVER_NAME = AttributeKey.stringKey("server.name");
	private static final AttributeKey<String> DIMENSION_NAME = AttributeKey.stringKey("dimension.name");
	private static final AttributeKey<String> ITEM_TYPE = AttributeKey.stringKey("item.type");
	private static final AttributeKey<Long> CHUNK_POS_X = AttributeKey.longKey("chunk.pos.x");
	private static final AttributeKey<Long> CHUNK_POS_Z = AttributeKey.longKey("chunk.pos.z");

	private final AtomicReference<LongGauge> itemEntityGauge = new AtomicReference<>();
	private final AtomicReference<ItemEntityCalculator> itemEntityCalculator = new AtomicReference<>();

	@Override
	@SubscribeEvent
	public void register(MetricEvent.RegisterMetricEvent event) {
		Metric.super.register(event);
		Meter meter = event.getMeterProvider().get(InstrumentationScopeNames.WORLD);
		itemEntityGauge.set(
				meter.gaugeBuilder(METRIC_NAME)
						.setDescription(DESCRIPTION)
						.setUnit(UNIT)
						.ofLongs()
						.build()
		);
	}

	@Override
	@SubscribeEvent
	public void unregister(MetricEvent.UnregisterMetricEvent event) {
		Metric.super.unregister(event);
		itemEntityGauge.set(null);
	}

	@SubscribeEvent
	public void onServerStart(ServerStartedEvent event) {
		itemEntityCalculator.set(new ItemEntityCalculator(event.getServer()));
	}

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		itemEntityCalculator.set(null);
	}

	@Override
	public long getWritingPeriod() {
		return TimeUnit.MINUTES.toSeconds(WRITING_PERIOD_MINUTE);
	}

	@Override
	public void write() {
		LongGauge gauge = itemEntityGauge.get();
		ItemEntityCalculator calculator = itemEntityCalculator.get();
		if (gauge == null || calculator == null) {
			return;
		}

		calculator.calculateAndWrite(gauge);
	}

	private static class ItemEntityCalculator {

		private final MinecraftServer server;

		ItemEntityCalculator(MinecraftServer server) {
			this.server = server;
		}

		void calculateAndWrite(LongGauge gauge) {
			if (server == null) {
				return;
			}

			String serverName = server.getWorldData().getLevelName();
			for (ServerLevel level : server.getAllLevels()) {
				String levelName = level.dimension().identifier().toString();
				Attributes baseAttributes = Attributes.of(SERVER_NAME, serverName, DIMENSION_NAME, levelName);

				Map<ItemEntityKey, Counter> counts = new HashMap<>();
				level.getEntities(EntityTypeTest.forClass(ItemEntity.class), (item) -> true)
						.forEach(itemEntity -> {
							Identifier itemKey = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
							if (itemKey == null) {
								return;
							}
							counts.computeIfAbsent(new ItemEntityKey(itemKey, itemEntity.chunkPosition()), k -> new Counter()).value++;
						});

				for (Map.Entry<ItemEntityKey, Counter> entry : counts.entrySet()) {
					ItemEntityKey key = entry.getKey();
					long chunkLong = key.chunkPos().pack();
					int x = ChunkPos.getX(chunkLong);
					int z = ChunkPos.getZ(chunkLong);

					AttributesBuilder builder = Attributes.builder()
							.putAll(baseAttributes)
							.put(ITEM_TYPE, key.itemKey().toString())
							.put(CHUNK_POS_X, (long) x)
							.put(CHUNK_POS_Z, (long) z);

					gauge.set(entry.getValue().value, builder.build());
				}
			}
		}

		private static class Counter {
			long value = 0;
		}
	}

	private record ItemEntityKey(Identifier itemKey, ChunkPos chunkPos) {

	}
}
