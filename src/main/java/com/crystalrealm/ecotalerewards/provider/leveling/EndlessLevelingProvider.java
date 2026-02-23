package com.crystalrealm.ecotalerewards.provider.leveling;

import com.crystalrealm.ecotalerewards.util.PluginLogger;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Level provider for <b>EndlessLeveling</b> by Airijko.
 *
 * <p>Uses reflection to call {@code EndlessLevelingAPI.get().grantXp(UUID, double)}.</p>
 */
public class EndlessLevelingProvider implements LevelProvider {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();
    private static final String API_CLASS = "com.airijko.endlessleveling.api.EndlessLevelingAPI";

    private boolean available;
    private Object apiInstance;
    private Method grantXpMethod;

    public EndlessLevelingProvider() {
        resolve();
    }

    private void resolve() {
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Method getMethod = apiClass.getMethod("get");
            apiInstance = getMethod.invoke(null);
            if (apiInstance == null) {
                LOGGER.info("EndlessLevelingAPI.get() returned null — provider disabled.");
                available = false;
                return;
            }
            grantXpMethod = apiInstance.getClass().getMethod("grantXp", UUID.class, double.class);
            available = true;
            LOGGER.info("EndlessLevelingAPI resolved successfully.");
        } catch (ClassNotFoundException e) {
            LOGGER.info("EndlessLeveling not found — provider disabled.");
            available = false;
        } catch (Exception e) {
            LOGGER.warn("Failed to resolve EndlessLevelingAPI: {}", e.getMessage());
            available = false;
        }
    }

    @Override
    public String getName() {
        return "Endless Leveling";
    }

    @Override
    public boolean isAvailable() {
        return available && apiInstance != null;
    }

    @Override
    public boolean grantXP(UUID playerUuid, double amount, String reason) {
        if (!isAvailable() || grantXpMethod == null) return false;
        try {
            grantXpMethod.invoke(apiInstance, playerUuid, amount);
            return true;
        } catch (Exception e) {
            LOGGER.warn("EndlessLeveling grantXp failed for {} ({}): {}", playerUuid, amount, e.getMessage());
            return false;
        }
    }
}
