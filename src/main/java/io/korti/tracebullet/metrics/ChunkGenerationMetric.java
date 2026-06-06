package io.korti.tracebullet.metrics;

import io.korti.tracebullet.api.InstrumentationScopeNames;
import io.korti.tracebullet.api.metrics.BaseMetric;
import io.korti.tracebullet.api.metrics.MetricEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reports newly generated chunks per dimension as a cumulative counter.
 *
 * <p>Uses {@link ChunkEvent.Load#isNewChunk()} to distinguish newly generated chunks from chunks
 * loaded from disk, counting only the former.
 *
 * <p>Instruments (scope {@code minecraft.world}):
 * <ul>
 *   <li>{@code minecraft.server.world.chunk.generated} — cumulative newly generated chunk counter</li>
 * </ul>
 * Attributes: {@code server.name}, {@code dimension.name}.
 */
public class ChunkGenerationMetric extends BaseMetric {

    private static final String CHUNK_GENERATED_METRIC_NAME = "minecraft.server.world.chunk.generated";
    private static final String CHUNK_GENERATED_DESCRIPTION = "Number of newly generated chunks (not loaded from disk).";
    private static final String CHUNK_UNIT = "{chunk}";

    private static final AttributeKey<String> SERVER_NAME = AttributeKey.stringKey("server.name");
    private static final AttributeKey<String> DIMENSION_NAME = AttributeKey.stringKey("dimension.name");

    private final AtomicReference<LongCounter> generatedCounter = new AtomicReference<>();

    @Override
    @SubscribeEvent
    public void register(MetricEvent.RegisterMetricEvent event) {
        super.register(event);
        generatedCounter.set(
                event.getMeterProvider().get(InstrumentationScopeNames.WORLD)
                        .counterBuilder(CHUNK_GENERATED_METRIC_NAME)
                        .setDescription(CHUNK_GENERATED_DESCRIPTION)
                        .setUnit(CHUNK_UNIT)
                        .build()
        );
    }

    @Override
    @SubscribeEvent
    public void unregister(MetricEvent.UnregisterMetricEvent event) {
        super.unregister(event);
        generatedCounter.set(null);
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        LongCounter counter = generatedCounter.get();
        if (counter == null || !event.isNewChunk()) {
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
        return TimeUnit.MINUTES.toSeconds(1);
    }

    @Override
    public void write() {}
}
