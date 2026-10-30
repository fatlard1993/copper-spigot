package justfatlard.copper_spigot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** {@code config/copper-spigot.json}, written with its defaults on first run. */
public final class CopperSpigotConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("copper-spigot.json");

	/** Off, lava is as endless as water: the source, or the lava cauldron, is left full. */
	public boolean lavaUsedUp = true;

	public static CopperSpigotConfig load() {
		CopperSpigotConfig config = new CopperSpigotConfig();
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				CopperSpigotConfig read = GSON.fromJson(reader, CopperSpigotConfig.class);
				if (read != null) config = read;
			} catch (IOException | RuntimeException e) {
				LOGGER.error("Failed to read {}, using defaults", PATH, e);
			}
		}
		config.save();
		return config;
	}

	public void setLavaUsedUp(boolean on) {
		lavaUsedUp = on;
		save();
	}

	private void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			LOGGER.error("Failed to write {}", PATH, e);
		}
	}
}
