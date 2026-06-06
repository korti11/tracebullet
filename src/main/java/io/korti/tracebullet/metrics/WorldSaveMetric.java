package io.korti.tracebullet.metrics;

import io.korti.tracebullet.api.InstrumentationScopeNames;
import io.korti.tracebullet.api.metrics.BaseMetric;
import io.korti.tracebullet.api.metrics.MetricEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.TimeUtil;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reports the duration of world save operations per dimension as a histogram.
 *
 * <p>Instruments (scope {@code minecraft.world}):
 * <ul>
 *   <li>{@code minecraft.server.world.save.duration} — time from server tick start to save
 *       completion, in ms, per dimension</li>
 * </ul>
 * Attributes: {@code server.name}, {@code dimension.name}.
 *
 * <p>Duration is measured from {@link ServerTickEvent.Pre} to {@link LevelEvent.Save}, so it
 * includes normal tick overhead (~50 ms baseline) in addition to the save itself.
 */
public class WorldSaveMetric extends BaseMetric {

    private static final String METRIC_NAME = "minecraft.server.world.save.duration";
    private static final String DESCRIPTION = "Duration of world save operations per dimension.";
    private static final String UNIT = "ms";

    private static final List<Double> BUCKETS = List.of(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0);

    private static final AttributeKey<String> SERVER_NAME = AttributeKey.stringKey("server.name");
    private static final AttributeKey<String> DIMENSION_NAME = AttributeKey.stringKey("dimension.name");

    private final AtomicReference<DoubleHistogram> saveDurationHistogram = new AtomicReference<>();
    private volatile long tickStartNs = 0;

    @Override
    @SubscribeEvent
    public void register(MetricEvent.RegisterMetricEvent event) {
        super.register(event);
        Meter meter = event.getMeterProvider().get(InstrumentationScopeNames.WORLD);
        saveDurationHistogram.set(
                meter.histogramBuilder(METRIC_NAME)
                        .setDescription(DESCRIPTION)
                        .setUnit(UNIT)
                        .setExplicitBucketBoundariesAdvice(BUCKETS)
                        .build()
        );
    }

    @Override
    @SubscribeEvent
    public void unregister(MetricEvent.UnregisterMetricEvent event) {
        super.unregister(event);
        saveDurationHistogram.set(null);
    }

    @SubscribeEvent
    public void onServerTickStart(ServerTickEvent.Pre event) {
        tickStartNs = System.nanoTime();
    }

    @SubscribeEvent
    public void onLevelSave(LevelEvent.Save event) {
        DoubleHistogram histogram = saveDurationHistogram.get();
        long startNs = tickStartNs;
        if (histogram == null || startNs == 0 || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        double durationMs = (System.nanoTime() - startNs) / (double) TimeUtil.NANOSECONDS_PER_MILLISECOND;
        Attributes attributes = attributesBuilder()
                .put(SERVER_NAME, level.getServer().getWorldData().getLevelName())
                .put(DIMENSION_NAME, level.dimension().identifier().toString())
                .build();

        histogram.record(durationMs, attributes);
    }

    @Override
    public void write() {
        // Event-driven: data is pushed directly in onLevelSave.
    }
}
