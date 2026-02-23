package com.crystalrealm.ecotalerewards.provider.leveling;

import com.crystalrealm.ecotalerewards.util.PluginLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Facade that routes XP operations to the active {@link LevelProvider}.
 */
public class LevelBridge {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final LinkedHashMap<String, LevelProvider> providers = new LinkedHashMap<>();
    private LevelProvider active;

    public LevelBridge() {
        registerProvider("rpgleveling", new RPGLevelingProvider());
        registerProvider("endlessleveling", new EndlessLevelingProvider());
    }

    public void registerProvider(@Nonnull String key, @Nonnull LevelProvider provider) {
        providers.put(key.toLowerCase(), provider);
        LOGGER.info("Level provider registered: {} ({})", key, provider.getName());
    }

    public boolean activate(@Nullable String preferredKey) {
        if (preferredKey != null) {
            LevelProvider p = providers.get(preferredKey.toLowerCase());
            if (p != null && p.isAvailable()) {
                active = p;
                LOGGER.info("Level provider activated: {} ({})", preferredKey, p.getName());
                return true;
            }
        }
        for (Map.Entry<String, LevelProvider> e : providers.entrySet()) {
            if (e.getValue().isAvailable()) {
                active = e.getValue();
                LOGGER.info("Level provider fallback: {} ({})", e.getKey(), active.getName());
                return true;
            }
        }
        LOGGER.warn("No level provider available — XP grants will be skipped.");
        return false;
    }

    public boolean isAvailable() {
        return active != null && active.isAvailable();
    }

    @Nonnull
    public String getProviderName() {
        return active != null ? active.getName() : "none";
    }

    public boolean grantXP(@Nonnull UUID playerUuid, double amount, @Nonnull String reason) {
        if (active == null || !active.isAvailable()) return false;
        return active.grantXP(playerUuid, amount, reason);
    }

    /** Notify all providers about a new player (caches ECS Store/Ref). */
    public void onPlayerJoin(UUID uuid, Object store, Object ref) {
        for (LevelProvider p : providers.values()) {
            try { p.onPlayerJoin(uuid, store, ref); } catch (Exception ignored) {}
        }
    }

    /** Notify all providers that a player left. */
    public void onPlayerLeave(UUID uuid) {
        for (LevelProvider p : providers.values()) {
            try { p.onPlayerLeave(uuid); } catch (Exception ignored) {}
        }
    }
}
