package io.korti.tracebullet.otel;

import io.korti.tracebullet.TraceBullet;
import io.korti.tracebullet.api.metrics.Metric;
import io.korti.tracebullet.api.metrics.MetricEvent;
import io.korti.tracebullet.api.metrics.MetricRegistry;
import io.korti.tracebullet.threading.ThreadPool;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class OpenTelemetryMetricRegistry implements MetricRegistry {

	private static final int INITIAL_DELAY_SECONDS = 30;
	private static final int WRITING_PERIOD_SECONDS = 10;

	private final Map<Class<? extends Metric>, ScheduledFuture<?>> runnables = new ConcurrentHashMap<>();

	private final IEventBus eventBus;
	private final ScheduledExecutorService metricsThreadPool;

	private OpenTelemetryMetricRegistry(IEventBus eventBus) {
		this.eventBus = eventBus;

		this.metricsThreadPool = TraceBullet.THREAD_POOL_MANAGER.getScheduledExecutorService(ThreadPool.METRIC);
	}

	public static MetricRegistry create(IEventBus eventBus) {
		requireNonNull(eventBus, "eventBus");

		return new OpenTelemetryMetricRegistry(eventBus);
	}

	@Override
	public void register(Metric metric) {
		ScheduledFuture<?> runnable = metricsThreadPool
				.scheduleAtFixedRate(metric::write, INITIAL_DELAY_SECONDS, WRITING_PERIOD_SECONDS, TimeUnit.SECONDS);

		runnables.put(metric.getClass(), runnable);
	}

	@Override
	public void unregister(Metric metric) {
		ScheduledFuture<?> runnable = runnables.remove(metric.getClass());
		runnable.cancel(true);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onServerStart(ServerStartedEvent event) {
		eventBus.post(new MetricEvent.RegisterMetricEvent(this));
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onServerStopping(ServerStoppingEvent event) {
		eventBus.post(new MetricEvent.UnregisterMetricEvent(this));
	}
}
