package com.crystalrealm.ecotalerewards.provider.economy;

import com.crystalrealm.ecotalerewards.util.PluginLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Facade that routes economy operations to the active {@link EconomyProvider}.
 */
public class EconomyBridge {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final LinkedHashMap<String, EconomyProvider> providers = new LinkedHashMap<>();
    private EconomyProvider active;

    public EconomyBridge() {
        registerProvider("ecotale", new EcotaleEconomyProvider());
        registerProvider("economyapi", new EconomyApiProvider());
    }

    public void registerProvider(@Nonnull String key, @Nonnull EconomyProvider provider) {
        providers.put(key.toLowerCase(), provider);
        LOGGER.info("Economy provider registered: {} ({})", key, provider.getName());
    }

    public boolean activate(@Nullable String preferredKey) {
        if (preferredKey != null) {
            EconomyProvider p = providers.get(preferredKey.toLowerCase());
            if (p != null && p.isAvailable()) {
                active = p;
                LOGGER.info("Economy provider activated: {} ({})", preferredKey, p.getName());
                return true;
            }
        }
        for (Map.Entry<String, EconomyProvider> e : providers.entrySet()) {
            if (e.getValue().isAvailable()) {
                active = e.getValue();
                LOGGER.info("Economy provider fallback: {} ({})", e.getKey(), active.getName());
                return true;
            }
        }
        LOGGER.warn("No economy provider available — deposits will fail.");
        return false;
    }

    public boolean isAvailable() {
        return active != null && active.isAvailable();
    }

    @Nonnull
    public String getProviderName() {
        return active != null ? active.getName() : "none";
    }

    public boolean deposit(@Nonnull UUID playerUuid, double amount, @Nonnull String reason) {
        if (active == null || !active.isAvailable()) {
            LOGGER.warn("Cannot deposit — no economy provider active.");
            return false;
        }
        return active.deposit(playerUuid, amount, reason);
    }
}
