package com.froggylord.constellation.config;

import com.froggylord.constellation.ConstellationClient;
import com.google.gson.*;

import java.io.*;
import java.nio.file.*;

public class ConfigManager {

    private static final Path CONFIG_PATH = Path.of("config", "constellation.json");

    private ConstellationConfig config;
    private final Gson gson;

    public ConfigManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                config = gson.fromJson(r, ConstellationConfig.class);
                if (config != null) {
                    config.migrate();
                    ConstellationClient.LOGGER.info("Config loaded (v{})", config.cfgVersion);
                    return;
                }
            } catch (Exception e) {
                ConstellationClient.LOGGER.error("Failed to load config, using defaults", e);
            }
        }
        config = new ConstellationConfig();
        save();
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                gson.toJson(config, w);
            }
        } catch (Exception e) {
            ConstellationClient.LOGGER.error("Failed to save config", e);
        }
    }

    public ConstellationConfig get() { return config; }

    public BaseConfigGroup getGroup(String constellationId) {
        if (config == null) return null;
        return config.getSubConfig(constellationId);
    }
}
