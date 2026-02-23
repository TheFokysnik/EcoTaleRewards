package com.crystalrealm.ecotalerewards.provider.leveling;

import com.crystalrealm.ecotalerewards.util.PluginLogger;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Config-driven reflection adapter for any leveling / XP plugin.
 */
public class GenericLevelProvider implements LevelProvider {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final String displayName;
    private Object apiInstance;
    private Method grantXPMethod;
    private boolean available;

    public GenericLevelProvider(String className, String instanceMethod, String grantXPName) {
        this.displayName = className;
        try {
            Class<?> clazz = Class.forName(className);

            if (instanceMethod != null && !instanceMethod.isBlank()) {
                Method accessor = clazz.getMethod(instanceMethod);
                apiInstance = accessor.invoke(null);
            }

            Class<?> target = apiInstance != null ? apiInstance.getClass() : clazz;

            // Try (UUID, double) first, then (UUID, int)
            try {
                grantXPMethod = target.getMethod(grantXPName, UUID.class, double.class);
            } catch (NoSuchMethodException e) {
                grantXPMethod = target.getMethod(grantXPName, UUID.class, int.class);
            }

            available = true;
            LOGGER.info("GenericLevelProvider resolved: {}", className);
        } catch (ClassNotFoundException e) {
            LOGGER.info("GenericLevelProvider class not found: {} (disabled)", className);
        } catch (Exception e) {
            LOGGER.warn("GenericLevelProvider init failed for {}: {}", className, e.getMessage());
        }
    }

    @Override
    public String getName() { return "Generic (" + displayName + ")"; }

    @Override
    public boolean isAvailable() { return available; }

    @Override
    public boolean grantXP(UUID playerUuid, double amount, String reason) {
        if (!available || grantXPMethod == null) return false;
        try {
            Class<?> paramType = grantXPMethod.getParameterTypes()[1];
            if (paramType == int.class) {
                grantXPMethod.invoke(apiInstance, playerUuid, (int) amount);
            } else {
                grantXPMethod.invoke(apiInstance, playerUuid, amount);
            }
            return true;
        } catch (Exception e) {
            LOGGER.debug("GenericLevelProvider.grantXP failed: {}", e.getMessage());
            return false;
        }
    }
}
