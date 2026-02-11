package com.crystalrealm.ecotalerewards.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.crystalrealm.ecotalerewards.util.PluginLogger;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages loading, saving and hot-reloading of JSON configuration.
 */
public class ConfigManager {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();
    private static final String CONFIG_FILENAME = "EcoTaleRewards.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path dataDirectory;
    private RewardsConfig config;

    public ConfigManager(@Nonnull Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    /**
     * Loads config from file or creates default from bundled resource.
     */
    public void loadOrCreate() {
        Path configPath = getConfigPath();

        try {
            Files.createDirectories(dataDirectory);

            if (Files.exists(configPath)) {
                loadFromFile(configPath);
            } else {
                createDefault(configPath);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load config: {}", e.getMessage());
            config = new RewardsConfig();
        }
    }

    /**
     * Hot-reload config from disk.
     *
     * @return true if successfully reloaded
     */
    public boolean reload() {
        Path configPath = getConfigPath();
        if (!Files.exists(configPath)) {
            LOGGER.warn("Config file not found: {}", configPath);
            return false;
        }

        try {
            loadFromFile(configPath);
            LOGGER.info("Configuration reloaded successfully.");
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to reload config: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Save current config state to disk.
     */
    public void saveConfig() {
        Path configPath = getConfigPath();
        try (Writer writer = new OutputStreamWriter(
                Files.newOutputStream(configPath), StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

    @Nonnull
    public RewardsConfig getConfig() {
        if (config == null) config = new RewardsConfig();
        return config;
    }

    @Nonnull
    public Path getConfigPath() {
        return dataDirectory.resolve(CONFIG_FILENAME);
    }

    @Nonnull
    public Path getDataDirectory() {
        return dataDirectory;
    }

    // ─── Private ──────────────────────────────────────────────────

    private void loadFromFile(Path path) throws IOException {
        try (Reader reader = new InputStreamReader(
                Files.newInputStream(path), StandardCharsets.UTF_8)) {
            config = GSON.fromJson(reader, RewardsConfig.class);
        }
        if (config == null) {
            LOGGER.warn("Config parsed as null, using defaults.");
            config = new RewardsConfig();
        }
    }

    private void createDefault(Path path) throws IOException {
        config = new RewardsConfig();

        try (InputStream defaultStream = getClass().getClassLoader()
                .getResourceAsStream("default-config.json")) {
            if (defaultStream != null) {
                Files.copy(defaultStream, path);
                loadFromFile(path);
                LOGGER.info("Default config created at {}", path);
                return;
            }
        }

        try (Writer writer = new OutputStreamWriter(
                Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        }
        LOGGER.info("Default config generated at {}", path);
    }
}
