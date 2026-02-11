package com.crystalrealm.ecotalerewards.returns;

import com.crystalrealm.ecotalerewards.config.RewardsConfig;
import com.crystalrealm.ecotalerewards.model.PlayerRewardData;
import com.crystalrealm.ecotalerewards.model.ReturnRewardTier;
import com.crystalrealm.ecotalerewards.util.PluginLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Return reward service — manages rewards for players returning
 * after extended absence.
 */
public class ReturnRewardService {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final RewardsConfig config;
    private final List<ReturnRewardTier> tiers = new ArrayList<>();

    public ReturnRewardService(@Nonnull RewardsConfig config) {
        this.config = config;
        loadTiers();
    }

    private void loadTiers() {
        tiers.clear();
        if (!config.getReturnRewards().isEnabled()) return;

        for (RewardsConfig.ReturnTierEntry entry : config.getReturnRewards().getTiers()) {
            tiers.add(new ReturnRewardTier(
                    entry.getMinAbsenceDays(),
                    entry.getMaxAbsenceDays(),
                    BigDecimal.valueOf(entry.getCoins()),
                    entry.getXP(),
                    entry.getItems() != null ? entry.getItems() : List.of(),
                    entry.getCommands() != null ? entry.getCommands() : List.of(),
                    entry.getDescription() != null ? entry.getDescription() : ""
            ));
        }
        tiers.sort(Comparator.comparingInt(ReturnRewardTier::getMinAbsenceDays));
        LOGGER.info("Loaded {} return reward tiers.", tiers.size());
    }

    public void reload() {
        loadTiers();
    }

    /**
     * Check if player is eligible for a return reward based on absence.
     * Updates player data flags.
     *
     * @param absenceDays days since last login
     */
    public void processAbsence(@Nonnull PlayerRewardData data, int absenceDays) {
        if (!config.getReturnRewards().isEnabled() || absenceDays <= 0) return;

        ReturnRewardTier tier = findTier(absenceDays);
        if (tier != null) {
            data.setPendingReturnReward(true);
            data.setAbsenceDays(absenceDays);
            LOGGER.info("Return reward pending for {}: {} days absent, tier {}+",
                    data.getPlayerUuid(), absenceDays, tier.getMinAbsenceDays());
        }
    }

    /**
     * Get the return reward tier for the player's absence duration.
     */
    @Nullable
    public ReturnRewardTier getPlayerReturnTier(@Nonnull PlayerRewardData data) {
        if (!data.isPendingReturnReward()) return null;
        return findTier(data.getAbsenceDays());
    }

    /**
     * Mark the return reward as claimed.
     */
    public void markReturnClaimed(@Nonnull PlayerRewardData data) {
        data.setPendingReturnReward(false);
        data.setAbsenceDays(0);
    }

    /**
     * Find the matching return reward tier for given absence days.
     */
    @Nullable
    public ReturnRewardTier findTier(int absenceDays) {
        // Find the best matching tier (highest min that still matches)
        ReturnRewardTier bestMatch = null;
        for (ReturnRewardTier tier : tiers) {
            if (tier.matches(absenceDays)) {
                if (bestMatch == null || tier.getMinAbsenceDays() > bestMatch.getMinAbsenceDays()) {
                    bestMatch = tier;
                }
            }
        }
        return bestMatch;
    }

    public boolean isEnabled() {
        return config.getReturnRewards().isEnabled();
    }

    @Nonnull
    public List<ReturnRewardTier> getAllTiers() {
        return List.copyOf(tiers);
    }
}
