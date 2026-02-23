package com.crystalrealm.ecotalerewards.provider.economy;

import com.crystalrealm.ecotalerewards.util.PluginLogger;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflection-based adapter for Ecotale Economy
 * ({@code com.ecotale.api.EcotaleAPI}).
 */
public class EcotaleEconomyProvider implements EconomyProvider {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();
    private static final String CLASS_NAME = "com.ecotale.api.EcotaleAPI";

    private Method depositMethod;
    private Method isAvailableMethod;
    private boolean resolved;

    public EcotaleEconomyProvider() {
        try {
            Class<?> clazz = Class.forName(CLASS_NAME);
            depositMethod = clazz.getMethod("deposit", UUID.class, double.class, String.class);
            isAvailableMethod = clazz.getMethod("isAvailable");
            resolved = true;
            LOGGER.info("Ecotale Economy API resolved.");
        } catch (ClassNotFoundException e) {
            LOGGER.info("Ecotale Economy not found (optional dependency).");
        } catch (Exception e) {
            LOGGER.warn("Failed to resolve Ecotale Economy API: {}", e.getMessage());
        }
    }

    @Override
    public String getName() { return "Ecotale Economy"; }

    @Override
    public boolean isAvailable() {
        if (!resolved || isAvailableMethod == null) return false;
        try {
            Object result = isAvailableMethod.invoke(null);
            return result instanceof Boolean b && b;
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean deposit(UUID playerUuid, double amount, String reason) {
        if (!isAvailable() || depositMethod == null) return false;
        try {
            Object result = depositMethod.invoke(null, playerUuid, amount, reason);
            return result instanceof Boolean b && b;
        } catch (Exception e) {
            LOGGER.warn("Ecotale deposit failed: {}", e.getMessage());
            return false;
        }
    }
}
