package io.korti.tracebullet.metrics;

import io.korti.tracebullet.api.InstrumentationScopeNames;
import io.korti.tracebullet.api.metrics.Metric;
import io.korti.tracebullet.api.metrics.MetricEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.Meter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class EntityMetrics implements Metric {
	private static final int WRITING_PERIOD_MINUTE = 1;

	private static final String ENTITY_LIVING_METRIC_NAME = "minecraft.server.world.entity.living";
	private static final String ENTITY_LIVING_DESCRIPTION = "Amount of living entities in the world.";

	private static final String UNIT = "{entity}";

	private static final AttributeKey<String> SERVER_NAME = AttributeKey.stringKey("server.name");
	private static final AttributeKey<String> DIMENSION_NAME = AttributeKey.stringKey("dimension.name");
	private static final AttributeKey<String> ENTITY_TYPE = AttributeKey.stringKey("entity.type");
	private static final AttributeKey<Long> CHUNK_POS_X = AttributeKey.longKey("chunk.pos.x");
	private static final AttributeKey<Long> CHUNK_POS_Z = AttributeKey.longKey("chunk.pos.z");

	private final AtomicReference<LongGauge> livingEntityGauge = new AtomicReference<>();
	private final AtomicReference<EntityCalculator> entityCalculator = new AtomicReference<>();

	@Override
	@SubscribeEvent
	public void register(MetricEvent.RegisterMetricEvent event) {
		Metric.super.register(event);
		Meter meter = event.getMeterProvider().get(InstrumentationScopeNames.WORLD);
		livingEntityGauge.set(
				meter.gaugeBuilder(ENTITY_LIVING_METRIC_NAME)
						.setDescription(ENTITY_LIVING_DESCRIPTION)
						.setUnit(UNIT)
						.ofLongs()
						.build()
		);
	}

	@Override
	@SubscribeEvent
	public void unregister(MetricEvent.UnregisterMetricEvent event) {
		Metric.super.unregister(event);
		livingEntityGauge.set(null);
	}

	@SubscribeEvent
	public void onServerStart(ServerStartedEvent event) {
		entityCalculator.set(new EntityCalculator(event.getServer()));
	}

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		entityCalculator.set(null);
	}

	@Override
	public long getWritingPeriod() {
		return TimeUnit.MINUTES.toSeconds(WRITING_PERIOD_MINUTE);
	}

	@Override
	public void write() {
		LongGauge livingEntities = livingEntityGauge.get();
		EntityCalculator calculator = entityCalculator.get();
		if (livingEntities == null || calculator == null) {
			return;
		}

		calculator.calculateAndWrite(livingEntities);
	}

	private static class EntityCalculator {

		private final MinecraftServer server;

		EntityCalculator(MinecraftServer server) {
			this.server = server;
		}

		void calculateAndWrite(LongGauge livingEntities) {
			if (server == null) {
				return;
			}

			String serverName = server.getWorldData().getLevelName();
			Iterable<ServerLevel> levels = server.getAllLevels();
			for (ServerLevel level : levels) {
				String levelName = level.dimension().identifier().toString();

				Attributes attributes = Attributes.of(SERVER_NAME, serverName, DIMENSION_NAME, levelName);
				Map<EntityKey, Long> entities = level.getEntities(EntityTypeTest.forClass(LivingEntity.class), (mob) -> true)
						.stream().collect(Collectors.groupingBy(EntityCalculator::createKey, Collectors.counting()));

				for (Map.Entry<EntityKey, Long> entry : entities.entrySet()) {
					EntityKey key = entry.getKey();

					long chunk = key.chunkPos().toLong();
					int x = ChunkPos.getX(chunk);
					int z = ChunkPos.getZ(chunk);

					AttributesBuilder builder = Attributes.builder()
							.putAll(attributes)
							.put(ENTITY_TYPE, EntityType.getKey(key.type()).toString())
							.put(CHUNK_POS_X, x)
							.put(CHUNK_POS_Z, z);

					livingEntities.set(entry.getValue(), builder.build());
				}
			}
		}

		private static EntityKey createKey(Entity entity) {
			return new EntityKey(entity.getType(), entity.chunkPosition());
		}
	}

	private record EntityKey (EntityType<?> type, ChunkPos chunkPos) {

	}
}
