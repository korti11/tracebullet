package io.korti.tracebullet;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
	private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

	//<editor-fold desc="OTel Section">
	public static final ModConfigSpec.ConfigValue<String> OTEL_URL = BUILDER.worldRestart()
			.comment("OpenTelemetry Collector URL")
			.define("otel.url", "", Config::urlValidator);
	public static final ModConfigSpec.ConfigValue<String> SERVICE_NAME_OVERRIDE = BUILDER.worldRestart()
			.comment("Override the value that is given to the \"service.name\" resource.")
			.comment("If this value is empty the world name is used for this resource.")
			.define("otel.service_name_override", "");
	public static final ModConfigSpec.ConfigValue<List<? extends String>> CUSTOM_ATTRIBUTES = BUILDER.worldRestart()
			.comment("Custom attributes that are added to the reported data.")
			.comment("Example: \"modpack.version=1.5.0\"")
			.defineListAllowEmpty("otel.custom_attributes", List.of(), () -> "key=value", Config::customAttributesValidator);
	//</editor-fold>

	//<editor-fold desc="Threading Section">
	public static final ModConfigSpec.IntValue SCHEDULE_THREAD_POOL_SIZE = BUILDER.gameRestart()
			.comment("Size of the background scheduled thread pool.")
			.comment("Initial default is calculated based on the available processors to the JVM.")
			.comment("(thread_pool_size=available_processors * 0.25)")
			.defineInRange("threading.schedule_thread_pool_size", Config::calculateDefaultThreadPoolSize, 1, 32);
	//</editor-fold>

	//<editor-fold desc="Metrics Section">
	public static final ModConfigSpec.BooleanValue METRICS_TPS_ENABLED = BUILDER.gameRestart()
			.comment("Enable the TPS and tick time metrics.")
			.define("metrics.tps", true);
	public static final ModConfigSpec.BooleanValue METRICS_PLAYER_ENABLED = BUILDER.gameRestart()
			.comment("Enable all player metrics (player count, latency).")
			.define("metrics.player", true);
	public static final ModConfigSpec.BooleanValue METRICS_WORLD_CHUNK_ENABLED = BUILDER.gameRestart()
			.comment("Enable the world chunk count metrics.")
			.define("metrics.world_chunk", true);
	public static final ModConfigSpec.BooleanValue METRICS_ENTITY_ENABLED = BUILDER.gameRestart()
			.comment("Enable the entity count metrics.")
			.define("metrics.entity", true);
	public static final ModConfigSpec.BooleanValue METRICS_JVM_ENABLED = BUILDER.gameRestart()
			.comment("Enable the JVM metrics.")
			.define("metrics.jvm", true);
	public static final ModConfigSpec.BooleanValue METRICS_BLOCK_ENTITY_ENABLED = BUILDER.gameRestart()
			.comment("Enable the block entity count metrics.")
			.define("metrics.block_entity", true);
	public static final ModConfigSpec.BooleanValue METRICS_ITEM_ENTITY_ENABLED = BUILDER.gameRestart()
			.comment("Enable the item entity count metrics.")
			.define("metrics.item_entity", true);
	public static final ModConfigSpec.BooleanValue METRICS_WORLD_SAVE_ENABLED = BUILDER.gameRestart()
			.comment("Enable the world save duration metric.")
			.define("metrics.world_save", true);
	//</editor-fold>

	static final ModConfigSpec SPEC = BUILDER.build();

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private static boolean urlValidator(Object element) {
		if (element instanceof String url) {
			if (StringUtils.isBlank(url)) {
				return true;
			}

			try {
				URI.create(url).toURL();
				return true;
			} catch (IllegalArgumentException | MalformedURLException e) {
				TraceBullet.LOGGER.warn("Given OTel URL is malformed: {}", e.getMessage());
				return false;
			}
		}
		return false;
	}

	private static boolean customAttributesValidator(Object element) {
		if (element instanceof String resource) {
			return Strings.CI.contains(resource, "=");
		}
		return false;
	}

	private static int calculateDefaultThreadPoolSize() {
		return (int) (Runtime.getRuntime().availableProcessors() * 0.25);
	}
}
