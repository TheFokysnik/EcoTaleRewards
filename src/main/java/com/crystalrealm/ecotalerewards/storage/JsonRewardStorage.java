package com.crystalrealm.ecotalerewards.storage;

import com.google.gson.*;
import com.crystalrealm.ecotalerewards.model.PlayerRewardData;
import com.crystalrealm.ecotalerewards.util.PluginLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON-file based storage for player reward data.
 * Each player gets their own file: players/{uuid}.json
 */
public class JsonRewardStorage implements RewardStorage {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path dataDirectory;
    private final Path playersDir;
    private final Map<UUID, PlayerRewardData> cache = new ConcurrentHashMap<>();

    public JsonRewardStorage(@Nonnull Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.playersDir = dataDirectory.resolve("players");
    }

    @Override
    public void initialize() {
        try {
            Files.createDirectories(playersDir);
            LOGGER.info("JsonRewardStorage initialized at {}", playersDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create storage directory: {}", e.getMessage());
        }
    }

    @Override
    @Nonnull
    public PlayerRewardData loadOrCreate(@Nonnull UUID playerUuid) {
        PlayerRewardData cached = cache.get(playerUuid);
        if (cached != null) return cached;

        Path file = playerFile(playerUuid);
        PlayerRewardData data;

        if (Files.exists(file)) {
            data = loadFromFile(file, playerUuid);
            if (data == null) data = new PlayerRewardData(playerUuid);
        } else {
            data = new PlayerRewardData(playerUuid);
        }

        cache.put(playerUuid, data);
        return data;
    }

    @Override
    @Nullable
    public PlayerRewardData getCached(@Nonnull UUID playerUuid) {
        return cache.get(playerUuid);
    }

    @Override
    public void savePlayer(@Nonnull UUID playerUuid) {
        PlayerRewardData data = cache.get(playerUuid);
        if (data == null) return;
        saveToFile(data);
    }

    @Override
    public void saveAll() {
        int count = 0;
        for (Map.Entry<UUID, PlayerRewardData> entry : cache.entrySet()) {
            saveToFile(entry.getValue());
            count++;
        }
        LOGGER.info("Saved {} player reward records.", count);
    }

    @Override
    public void evict(@Nonnull UUID playerUuid) {
        savePlayer(playerUuid);
        cache.remove(playerUuid);
    }

    @Override
    @Nonnull
    public Collection<UUID> getCachedPlayerUuids() {
        return Collections.unmodifiableSet(cache.keySet());
    }

    @Override
    public int getTrackedPlayerCount() {
        try {
            return (int) Files.list(playersDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .count();
        } catch (IOException e) {
            return cache.size();
        }
    }

    @Override
    public void deletePlayer(@Nonnull UUID playerUuid) {
        cache.remove(playerUuid);
        Path file = playerFile(playerUuid);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.warn("Failed to delete player file: {}", e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        saveAll();
        cache.clear();
        LOGGER.info("JsonRewardStorage shut down.");
    }

    // ═════════════════════════════════════════════════════════
    //  PRIVATE
    // ═════════════════════════════════════════════════════════

    private Path playerFile(UUID uuid) {
        return playersDir.resolve(uuid.toString() + ".json");
    }

    @Nullable
    private PlayerRewardData loadFromFile(Path file, UUID uuid) {
        try (Reader reader = new InputStreamReader(
                Files.newInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return null;

            PlayerRewardData data = new PlayerRewardData(uuid);
            data.setCurrentDay(getInt(json, "currentDay", 1));
            data.setStreak(getInt(json, "streak", 0));
            data.setLongestStreak(getInt(json, "longestStreak", 0));
            data.setTotalClaimed(getInt(json, "totalClaimed", 0));
            data.setPendingReturnReward(getBool(json, "pendingReturnReward", false));
            data.setAbsenceDays(getInt(json, "absenceDays", 0));

            String lastLogin = getString(json, "lastLoginDate", null);
            if (lastLogin != null) data.setLastLoginDate(LocalDate.parse(lastLogin));

            String lastClaim = getString(json, "lastClaimDate", null);
            if (lastClaim != null) data.setLastClaimDate(LocalDate.parse(lastClaim));

            if (json.has("claimedDays") && json.get("claimedDays").isJsonArray()) {
                for (JsonElement el : json.getAsJsonArray("claimedDays")) {
                    data.addClaimedDay(el.getAsInt());
                }
                // undo totalClaimed increments from addClaimedDay
                data.setTotalClaimed(getInt(json, "totalClaimed", 0));
            }

            return data;
        } catch (Exception e) {
            LOGGER.error("Failed to load player data from {}: {}", file, e.getMessage());
            return null;
        }
    }

    private void saveToFile(PlayerRewardData data) {
        Path file = playerFile(data.getPlayerUuid());
        try {
            Files.createDirectories(file.getParent());

            JsonObject json = new JsonObject();
            json.addProperty("playerUuid", data.getPlayerUuid().toString());
            json.addProperty("currentDay", data.getCurrentDay());
            json.addProperty("streak", data.getStreak());
            json.addProperty("longestStreak", data.getLongestStreak());
            json.addProperty("totalClaimed", data.getTotalClaimed());
            json.addProperty("pendingReturnReward", data.isPendingReturnReward());
            json.addProperty("absenceDays", data.getAbsenceDays());

            if (data.getLastLoginDate() != null)
                json.addProperty("lastLoginDate", data.getLastLoginDate().toString());
            if (data.getLastClaimDate() != null)
                json.addProperty("lastClaimDate", data.getLastClaimDate().toString());

            JsonArray claimedArr = new JsonArray();
            for (int day : data.getClaimedDays()) {
                claimedArr.add(day);
            }
            json.add("claimedDays", claimedArr);

            try (Writer writer = new OutputStreamWriter(
                    Files.newOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save player data for {}: {}", data.getPlayerUuid(), e.getMessage());
        }
    }

    // ── JSON helpers ────────────────────────────────────────────

    private static int getInt(JsonObject json, String key, int def) {
        return json.has(key) ? json.get(key).getAsInt() : def;
    }

    private static boolean getBool(JsonObject json, String key, boolean def) {
        return json.has(key) ? json.get(key).getAsBoolean() : def;
    }

    @Nullable
    private static String getString(JsonObject json, String key, @Nullable String def) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : def;
    }
}
