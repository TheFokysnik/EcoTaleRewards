package com.crystalrealm.ecotalerewards.provider.leveling;

import java.util.UUID;

/**
 * Universal interface for any leveling / XP system.
 *
 * <p>Built-in implementations:
 * <ul>
 *   <li>{@link RPGLevelingProvider} — Zuxaw RPG Leveling</li>
 *   <li>{@link EndlessLevelingProvider} — EndlessLeveling by Airijko</li>
 *   <li>{@link MMOSkillTreeProvider} — MMOSkillTree by Ziggfreed</li>
 *   <li>{@link GenericLevelProvider} — reflection adapter for any plugin</li>
 * </ul>
 */
public interface LevelProvider {

    /** Human-readable provider name. */
    String getName();

    /** Whether the backing leveling plugin is loaded and callable. */
    boolean isAvailable();

    /**
     * Grants XP to a player.
     *
     * @param playerUuid player UUID
     * @param amount     XP amount
     * @param reason     reason for logging
     * @return true if successful
     */
    boolean grantXP(UUID playerUuid, double amount, String reason);

    /**
     * Called when a player joins. Providers that need ECS context (Store/Ref)
     * can cache it here.
     */
    default void onPlayerJoin(UUID uuid, Object store, Object ref) {}

    /** Called when a player leaves. */
    default void onPlayerLeave(UUID uuid) {}
}
