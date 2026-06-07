package io.korti.tracebullet;

import com.mojang.logging.LogUtils;
import io.korti.tracebullet.metrics.*;
import io.korti.tracebullet.otel.OpenTelemetryMetricRegistry;
import io.korti.tracebullet.otel.OpenTelemetrySetupHandler;
import io.korti.tracebullet.threading.ThreadPool;
import io.korti.tracebullet.threading.ThreadPoolManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.function.Supplier;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(TraceBullet.MODID)
public class TraceBullet {
	// Define mod id in a common place for everything to reference
	public static final String MODID = "tracebullet";
	// Directly reference a slf4j logger
	public static final Logger LOGGER = LogUtils.getLogger();
	// Manager of thread pools for the mod
	public static final ThreadPoolManager THREAD_POOL_MANAGER = new ThreadPoolManager();

	// The constructor for the mod class is the first code that is run when your mod is loaded.
	// FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
	public TraceBullet(IEventBus modEventBus, ModContainer modContainer) {
		// Register the commonSetup method for modloading
		modEventBus.addListener(this::commonSetup);

		// Register ourselves for server and other game events we are interested in.
		// Note that this is necessary if and only if we want *this* class (TraceBullet) to respond directly to events.
		// Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
		NeoForge.EVENT_BUS.register(new OpenTelemetrySetupHandler());

		// Register our mod's ModConfigSpec so that FML can create and load the config file for us
		modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
	}

	private void commonSetup(FMLCommonSetupEvent event) {
		// Register thread pools
		THREAD_POOL_MANAGER.registerScheduledThreadPool(ThreadPool.METRIC, Config.SCHEDULE_THREAD_POOL_SIZE);
		NeoForge.EVENT_BUS.register(OpenTelemetryMetricRegistry.create(NeoForge.EVENT_BUS));
		registerMetrics();
	}

	private void registerMetricIf(ModConfigSpec.BooleanValue flag, Supplier<Object> factory) {
		if (flag.get()) {
			NeoForge.EVENT_BUS.register(factory.get());
		}
	}

	private void registerMetrics() {
		registerMetricIf(Config.METRICS_TPS_ENABLED, TPSMetric::new);
		registerMetricIf(Config.METRICS_PLAYER_ENABLED, PlayerCountMetric::new);
		registerMetricIf(Config.METRICS_WORLD_CHUNK_ENABLED, WorldChunkMetrics::new);
		registerMetricIf(Config.METRICS_ENTITY_ENABLED, EntityMetrics::new);
		registerMetricIf(Config.METRICS_JVM_ENABLED, JvmMetrics::new);
		registerMetricIf(Config.METRICS_BLOCK_ENTITY_ENABLED, BlockEntityMetrics::new);
		registerMetricIf(Config.METRICS_ITEM_ENTITY_ENABLED, ItemEntityMetrics::new);
		registerMetricIf(Config.METRICS_PLAYER_ENABLED, PlayerLatencyMetric::new);
		registerMetricIf(Config.METRICS_WORLD_SAVE_ENABLED, WorldSaveMetric::new);
		registerMetricIf(Config.METRICS_SCHEDULED_TICK_ENABLED, ScheduledTickMetric::new);
		registerMetricIf(Config.METRICS_CHUNK_GENERATION_ENABLED, ChunkGenerationMetric::new);
	}
}
