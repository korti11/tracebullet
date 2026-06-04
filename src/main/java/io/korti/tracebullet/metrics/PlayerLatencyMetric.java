package io.korti.tracebullet.metrics;

import io.korti.tracebullet.api.InstrumentationScopeNames;
import io.korti.tracebullet.api.metrics.BaseMetric;
import io.korti.tracebullet.api.metrics.MetricEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.Meter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.concurrent.atomic.AtomicReference;

public class PlayerLatencyMetric extends BaseMetric {

	private static final String METRIC_NAME = "minecraft.server.player.latency";
	private static final String DESCRIPTION = "Number of players grouped by connection latency range.";
	private static final String UNIT = "count";

	private static final int[] BUCKET_THRESHOLDS = {50, 150, 300};
	private static final String[] BUCKET_LABELS = {"0-49", "50-149", "150-299", "300+"};

	private static final AttributeKey<String> SERVER_NAME = AttributeKey.stringKey("server.name");
	private static final AttributeKey<String> LATENCY_RANGE = AttributeKey.stringKey("latency.range");

	private final AtomicReference<LongGauge> latencyGauge = new AtomicReference<>();
	private final AtomicReference<MinecraftServer> serverRef = new AtomicReference<>();

	@Override
	@SubscribeEvent
	public void register(MetricEvent.RegisterMetricEvent event) {
		super.register(event);
		Meter meter = event.getMeterProvider().get(InstrumentationScopeNames.SERVER);
		latencyGauge.set(meter.gaugeBuilder(METRIC_NAME)
				.ofLongs().setDescription(DESCRIPTION).setUnit(UNIT).build());
	}

	@Override
	@SubscribeEvent
	public void unregister(MetricEvent.UnregisterMetricEvent event) {
		super.unregister(event);
		latencyGauge.set(null);
	}

	@SubscribeEvent
	public void onServerStart(ServerStartedEvent event) {
		serverRef.set(event.getServer());
	}

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		serverRef.set(null);
	}

	@Override
	public void write() {
		LongGauge gauge = latencyGauge.get();
		MinecraftServer server = serverRef.get();
		if (gauge == null || server == null) {
			return;
		}

		String serverName = server.getWorldData().getLevelName();
		long[] buckets = new long[BUCKET_LABELS.length];

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int latency = player.connection.latency();
			int bucket = BUCKET_LABELS.length - 1;
			for (int i = 0; i < BUCKET_THRESHOLDS.length; i++) {
				if (latency < BUCKET_THRESHOLDS[i]) {
					bucket = i;
					break;
				}
			}
			buckets[bucket]++;
		}

		for (int i = 0; i < BUCKET_LABELS.length; i++) {
			gauge.set(buckets[i], attributesBuilder().put(SERVER_NAME, serverName).put(LATENCY_RANGE, BUCKET_LABELS[i]).build());
		}
	}
}
