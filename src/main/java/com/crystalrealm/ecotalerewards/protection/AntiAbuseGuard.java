package com.crystalrealm.ecotalerewards.protection;

import com.crystalrealm.ecotalerewards.config.RewardsConfig;
import com.crystalrealm.ecotalerewards.model.PlayerRewardData;
import com.crystalrealm.ecotalerewards.util.PluginLogger;

import javax.annotation.Nonnull;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-abuse guard — prevents reward exploitation.
 * Checks: minimum online time, relog cooldown, max claims per day.
 */
public class AntiAbuseGuard {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final RewardsConfig config;

    /** Tracks last claim timestamp per player (epoch millis). */
    private final Map<UUID, Long> lastClaimTime = new ConcurrentHashMap<>();

    public AntiAbuseGuard(@Nonnull RewardsConfig config) {
        this.config = config;
    }

    /**
     * Check if the player has been online long enough to claim.
     */
    public boolean hasMinOnlineTime(@Nonnull PlayerRewardData data) {
        int minMinutes = config.getAntiAbuse().getMinOnlineMinutes();
        if (minMinutes <= 0) return true;

        long joinTime = data.getSessionJoinTime();
        if (joinTime <= 0) return false;

        long elapsedMs = System.currentTimeMillis() - joinTime;
        long requiredMs = (long) minMinutes * 60 * 1000;

        return elapsedMs >= requiredMs;
    }

    /**
     * Check if the relog cooldown has passed since last claim.
     */
    public boolean hasPassedRelogCooldown(@Nonnull UUID playerUuid) {
        int cooldownMinutes = config.getAntiAbuse().getRelogCooldownMinutes();
        if (cooldownMinutes <= 0) return true;

        Long lastClaim = lastClaimTime.get(playerUuid);
        if (lastClaim == null) return true;

        long elapsedMs = System.currentTimeMillis() - lastClaim;
        long cooldownMs = (long) cooldownMinutes * 60 * 1000;

        return elapsedMs >= cooldownMs;
    }

    /**
     * Check if the player is within daily claim limit.
     */
    public boolean isWithinDailyLimit(@Nonnull PlayerRewardData data, @Nonnull LocalDate today) {
        int maxClaims = config.getAntiAbuse().getMaxClaimsPerDay();
        if (maxClaims <= 0) return true;
        return data.getClaimsToday(today) < maxClaims;
    }

    /**
     * Record a claim for cooldown tracking.
     */
    public void recordClaim(@Nonnull UUID playerUuid) {
        lastClaimTime.put(playerUuid, System.currentTimeMillis());
    }

    /**
     * Full check — returns a reason string if blocked, null if allowed.
     */
    @javax.annotation.Nullable
    public String checkCanClaim(@Nonnull PlayerRewardData data, @Nonnull LocalDate today) {
        UUID uuid = data.getPlayerUuid();

        if (!hasMinOnlineTime(data)) {
            return "min_online";
        }
        if (!hasPassedRelogCooldown(uuid)) {
            return "cooldown";
        }
        if (!isWithinDailyLimit(data, today)) {
            return "max_claims";
        }
        return null;
    }

    /**
     * Cleanup on shutdown.
     */
    public void cleanup() {
        lastClaimTime.clear();
    }
}
