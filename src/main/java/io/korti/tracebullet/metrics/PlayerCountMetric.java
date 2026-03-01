package io.korti.tracebullet.metrics;

import io.korti.tracebullet.api.InstrumentationScopeNames;
import io.korti.tracebullet.api.metrics.Metric;
import io.korti.tracebullet.api.metrics.MetricEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PlayerCountMetric implements Metric {

	private static final int WRITING_PERIOD_MINUTE = 1;

	private static final String METRIC_NAME = "minecraft.server.player.count";
	private static final String DESCRIPTION = "Current connected players.";
	private static final String UNIT = "count";

	private static final AttributeKey<String> SERVER_NAME = AttributeKey.stringKey("server.name");

	private final AtomicReference<LongCounter> playerCounter = new AtomicReference<>();
	private final AtomicReference<MinecraftServer> minecraftServerRef = new AtomicReference<>();

	@Override
	@SubscribeEvent
	public void register(MetricEvent.RegisterMetricEvent event) {
		Metric.super.register(event);
		Meter meter = event.getMeterProvider().get(InstrumentationScopeNames.SERVER);
		playerCounter.set(meter.counterBuilder(METRIC_NAME).setDescription(DESCRIPTION).setUnit(UNIT).build());
	}

	@Override
	@SubscribeEvent
	public void unregister(MetricEvent.UnregisterMetricEvent event) {
		Metric.super.unregister(event);
		playerCounter.set(null);
	}

	@SubscribeEvent
	public void onServerStart(ServerStartedEvent event) {
		minecraftServerRef.set(event.getServer());
	}

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		minecraftServerRef.set(null);
	}

	@Override
	public long getWritingPeriod() {
		return TimeUnit.MINUTES.toSeconds(WRITING_PERIOD_MINUTE);
	}

	@Override
	public void write() {
		MinecraftServer server = minecraftServerRef.get();
		LongCounter counter = playerCounter.get();

		String serverName = server.getWorldData().getLevelName();
		counter.add(server.getPlayerCount(), Attributes.of(SERVER_NAME, serverName));
	}
}
