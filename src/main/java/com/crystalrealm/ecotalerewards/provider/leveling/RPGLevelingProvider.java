package com.crystalrealm.ecotalerewards.provider.leveling;

import com.crystalrealm.ecotalerewards.util.PluginLogger;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflection-based adapter for Zuxaw RPG Leveling
 * ({@code org.zuxaw.plugin.api.RPGLevelingAPI}).
 */
public class RPGLevelingProvider implements LevelProvider {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();
    private static final String CLASS_NAME = "org.zuxaw.plugin.api.RPGLevelingAPI";

    private Object api;
    private Method addXPMethod;
    private boolean available;

    public RPGLevelingProvider() {
        try {
            Class<?> clazz = Class.forName(CLASS_NAME);

            // Resolve singleton
            for (String name : new String[]{"get", "getInstance", "getAPI"}) {
                try {
                    Method m = clazz.getMethod(name);
                    api = m.invoke(null);
                    if (api != null) break;
                } catch (NoSuchMethodException ignored) {}
            }

            if (api != null) {
                // Try addXP(UUID, double) first, then addExperience
                for (String name : new String[]{"addXP", "addExperience", "grantXP"}) {
                    try {
                        addXPMethod = api.getClass().getMethod(name, UUID.class, double.class);
                        break;
                    } catch (NoSuchMethodException ignored) {}
                }
                available = (addXPMethod != null);
                LOGGER.info("RPG Leveling API resolved (grantXP via {}).",
                        addXPMethod != null ? addXPMethod.getName() : "N/A");
            }
        } catch (ClassNotFoundException e) {
            LOGGER.info("RPG Leveling not found (optional dependency).");
        } catch (Exception e) {
            LOGGER.warn("Failed to resolve RPG Leveling API: {}", e.getMessage());
        }
    }

    @Override
    public String getName() { return "RPG Leveling"; }

    @Override
    public boolean isAvailable() { return available && api != null; }

    @Override
    public boolean grantXP(UUID playerUuid, double amount, String reason) {
        if (!isAvailable() || addXPMethod == null) return false;
        try {
            addXPMethod.invoke(api, playerUuid, amount);
            return true;
        } catch (Exception e) {
            LOGGER.warn("grantXP failed for {}: {}", playerUuid, e.getMessage());
            return false;
        }
    }
}
