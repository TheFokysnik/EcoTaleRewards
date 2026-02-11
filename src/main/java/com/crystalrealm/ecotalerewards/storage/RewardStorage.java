package com.crystalrealm.ecotalerewards.storage;

import com.crystalrealm.ecotalerewards.model.PlayerRewardData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;

/**
 * DAO interface for player reward data persistence.
 * Implementations: {@link JsonRewardStorage} (file-based JSON).
 */
public interface RewardStorage {

    /** Initialize storage (create directories, load cache). */
    void initialize();

    /** Load or create data for a player. */
    @Nonnull
    PlayerRewardData loadOrCreate(@Nonnull UUID playerUuid);

    /** Get cached data (may be null if not loaded). */
    @Nullable
    PlayerRewardData getCached(@Nonnull UUID playerUuid);

    /** Save a single player's data. */
    void savePlayer(@Nonnull UUID playerUuid);

    /** Save all cached data to disk. */
    void saveAll();

    /** Remove player data from cache. */
    void evict(@Nonnull UUID playerUuid);

    /** Get all currently cached player UUIDs. */
    @Nonnull
    Collection<UUID> getCachedPlayerUuids();

    /** Get count of tracked players. */
    int getTrackedPlayerCount();

    /** Delete a player's data completely. */
    void deletePlayer(@Nonnull UUID playerUuid);

    /** Shutdown — flush and close. */
    void shutdown();
}
