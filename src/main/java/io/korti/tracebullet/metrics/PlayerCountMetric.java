package io.korti.tracebullet.metrics;

import io.korti.tracebullet.api.InstrumentationScopeNames;
import io.korti.tracebullet.api.metrics.BaseMetric;
import io.korti.tracebullet.api.metrics.MetricEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.Meter;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reports the number of players currently connected to the server.
 *
 * <p>Instruments (scope {@code minecraft.server}):
 * <ul>
 *   <li>{@code minecraft.server.player.count} — player count gauge, written every minute</li>
 * </ul>
 * Attributes: {@code server.name}.
 */
public class PlayerCountMetric extends BaseMetric {

	private static final int WRITING_PERIOD_MINUTE = 1;

	private static final String METRIC_NAME = "minecraft.server.player.count";
	private static final String DESCRIPTION = "Current connected players.";
	private static final String UNIT = "{player}";

	private static final AttributeKey<String> SERVER_NAME = AttributeKey.stringKey("server.name");

	private final AtomicReference<LongGauge> playerGauge = new AtomicReference<>();
	private final AtomicReference<MinecraftServer> minecraftServerRef = new AtomicReference<>();

	@Override
	@SubscribeEvent
	public void register(MetricEvent.RegisterMetricEvent event) {
		super.register(event);
		Meter meter = event.getMeterProvider().get(InstrumentationScopeNames.SERVER);
		playerGauge.set(meter.gaugeBuilder(METRIC_NAME).setDescription(DESCRIPTION).setUnit(UNIT).ofLongs().build());
	}

	@Override
	@SubscribeEvent
	public void unregister(MetricEvent.UnregisterMetricEvent event) {
		super.unregister(event);
		playerGauge.set(null);
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
		LongGauge gauge = playerGauge.get();

		String serverName = server.getWorldData().getLevelName();
		gauge.set(server.getPlayerCount(), attributesBuilder().put(SERVER_NAME, serverName).build());
	}
}
