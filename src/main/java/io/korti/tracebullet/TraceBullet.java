package io.korti.tracebullet;

import io.korti.tracebullet.metrics.BlockEntityMetrics;
import io.korti.tracebullet.metrics.EntityMetrics;
import io.korti.tracebullet.metrics.JvmMemoryMetric;
import io.korti.tracebullet.metrics.PlayerCountMetric;
import io.korti.tracebullet.metrics.TPSMetric;
import io.korti.tracebullet.metrics.WorldChunkMetrics;
import io.korti.tracebullet.otel.OpenTelemetryMetricRegistry;
import io.korti.tracebullet.otel.OpenTelemetrySetupHandler;
import io.korti.tracebullet.threading.ThreadPool;
import io.korti.tracebullet.threading.ThreadPoolManager;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

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
		NeoForge.EVENT_BUS.register(this);
		NeoForge.EVENT_BUS.register(new OpenTelemetrySetupHandler());

		// Register our mod's ModConfigSpec so that FML can create and load the config file for us
		modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
	}

	private void commonSetup(FMLCommonSetupEvent event) {
		// Some common setup code
		LOGGER.info("HELLO FROM COMMON SETUP");

		// Register thread pools
		THREAD_POOL_MANAGER.registerScheduledThreadPool(ThreadPool.METRIC, Config.SCHEDULE_THREAD_POOL_SIZE);
		NeoForge.EVENT_BUS.register(OpenTelemetryMetricRegistry.create(NeoForge.EVENT_BUS));
		registerMetrics();
	}

	private void registerMetrics() {
		NeoForge.EVENT_BUS.register(new TPSMetric());
		NeoForge.EVENT_BUS.register(new PlayerCountMetric());
		NeoForge.EVENT_BUS.register(new WorldChunkMetrics());
		NeoForge.EVENT_BUS.register(new EntityMetrics());
		NeoForge.EVENT_BUS.register(new JvmMemoryMetric());
		NeoForge.EVENT_BUS.register(new BlockEntityMetrics());
	}

	// You can use SubscribeEvent and let the Event Bus discover methods to call
	@SubscribeEvent
	public void onServerStarting(ServerStartingEvent event) {
		// Do something when the server starts
		LOGGER.info("HELLO from server starting");
	}
}
