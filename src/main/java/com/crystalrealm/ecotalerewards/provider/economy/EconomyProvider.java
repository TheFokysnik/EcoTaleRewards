package com.crystalrealm.ecotalerewards.provider.economy;

import java.util.UUID;

/**
 * Universal interface for any economy plugin.
 *
 * <p>Built-in implementations:
 * <ul>
 *   <li>{@link EcotaleEconomyProvider} — Ecotale Economy</li>
 *   <li>{@link GenericEconomyProvider} — reflection adapter for any plugin</li>
 * </ul>
 */
public interface EconomyProvider {

    /** Human-readable provider name. */
    String getName();

    /** Whether the backing economy plugin is loaded and callable. */
    boolean isAvailable();

    /**
     * Deposits currency to a player's account.
     *
     * @param playerUuid player UUID
     * @param amount     amount to deposit
     * @param reason     human-readable reason for the transaction
     * @return true if the deposit succeeded
     */
    boolean deposit(UUID playerUuid, double amount, String reason);
}
