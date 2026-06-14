package io.korti.tracebullet;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(value = TraceBullet.MODID, dist = Dist.DEDICATED_SERVER)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = TraceBullet.MODID, value = Dist.DEDICATED_SERVER)
public class TraceBulletServer {

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		TraceBullet.LOGGER.info("Start shutting down thread pool manager.");
		TraceBullet.THREAD_POOL_MANAGER.shutdown();
	}

}