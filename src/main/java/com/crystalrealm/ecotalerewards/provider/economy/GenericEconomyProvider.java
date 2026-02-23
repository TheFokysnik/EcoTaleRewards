package com.crystalrealm.ecotalerewards.provider.economy;

import com.crystalrealm.ecotalerewards.util.PluginLogger;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Config-driven reflection adapter for any economy plugin.
 *
 * <p>Works with any API that exposes a {@code deposit(UUID, double[, String])} method.
 * Supports both static and singleton-based APIs.</p>
 */
public class GenericEconomyProvider implements EconomyProvider {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final String displayName;
    private Object apiInstance;
    private Method depositMethod;
    private boolean depositHasReason;
    private boolean available;

    public GenericEconomyProvider(String className, String instanceMethod,
                                 String depositMethodName, boolean depositHasReason) {
        this.displayName = className;
        this.depositHasReason = depositHasReason;
        try {
            Class<?> clazz = Class.forName(className);

            if (instanceMethod != null && !instanceMethod.isBlank()) {
                Method accessor = clazz.getMethod(instanceMethod);
                apiInstance = accessor.invoke(null);
            }

            Class<?> target = apiInstance != null ? apiInstance.getClass() : clazz;

            // Try with reason first, then without
            if (depositHasReason) {
                try {
                    depositMethod = target.getMethod(depositMethodName, UUID.class, double.class, String.class);
                } catch (NoSuchMethodException e) {
                    depositMethod = target.getMethod(depositMethodName, UUID.class, double.class);
                    this.depositHasReason = false;
                }
            } else {
                try {
                    depositMethod = target.getMethod(depositMethodName, UUID.class, double.class);
                } catch (NoSuchMethodException e) {
                    depositMethod = target.getMethod(depositMethodName, UUID.class, double.class, String.class);
                    this.depositHasReason = true;
                }
            }

            available = true;
            LOGGER.info("GenericEconomyProvider resolved: {}", className);
        } catch (ClassNotFoundException e) {
            LOGGER.info("GenericEconomyProvider class not found: {} (disabled)", className);
        } catch (Exception e) {
            LOGGER.warn("GenericEconomyProvider init failed for {}: {}", className, e.getMessage());
        }
    }

    @Override
    public String getName() { return "Generic (" + displayName + ")"; }

    @Override
    public boolean isAvailable() { return available; }

    @Override
    public boolean deposit(UUID playerUuid, double amount, String reason) {
        if (!available || depositMethod == null) return false;
        try {
            Object result;
            if (depositHasReason) {
                result = depositMethod.invoke(apiInstance, playerUuid, amount, reason);
            } else {
                result = depositMethod.invoke(apiInstance, playerUuid, amount);
            }
            return !(result instanceof Boolean b) || b;
        } catch (Exception e) {
            LOGGER.warn("GenericEconomyProvider.deposit failed: {}", e.getMessage());
            return false;
        }
    }
}
