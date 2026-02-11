package com.crystalrealm.ecotalerewards.streaks;

import com.crystalrealm.ecotalerewards.config.RewardsConfig;
import com.crystalrealm.ecotalerewards.model.PlayerRewardData;
import com.crystalrealm.ecotalerewards.model.StreakMilestone;
import com.crystalrealm.ecotalerewards.util.PluginLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.*;

/**
 * Streak service — tracks consecutive login days, calculates multipliers,
 * detects milestones, and handles streak breaks.
 */
public class StreakService {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final RewardsConfig config;
    private final List<StreakMilestone> milestones = new ArrayList<>();

    public StreakService(@Nonnull RewardsConfig config) {
        this.config = config;
        loadMilestones();
    }

    private void loadMilestones() {
        milestones.clear();
        if (!config.getStreak().isEnabled()) return;

        Map<String, RewardsConfig.MilestoneEntry> entries = config.getStreak().getMilestones();
        for (Map.Entry<String, RewardsConfig.MilestoneEntry> entry : entries.entrySet()) {
            try {
                int days = Integer.parseInt(entry.getKey());
                RewardsConfig.MilestoneEntry m = entry.getValue();
                milestones.add(new StreakMilestone(
                        days,
                        BigDecimal.valueOf(m.getBonusCoins()),
                        m.getBonusXP(),
                        m.getRewardMultiplier(),
                        m.getCommands() != null ? m.getCommands() : List.of(),
                        m.getDescription() != null ? m.getDescription() : ""
                ));
            } catch (NumberFormatException ignored) {
                LOGGER.warn("Invalid milestone key: {}", entry.getKey());
            }
        }
        milestones.sort(Comparator.comparingInt(StreakMilestone::getDays));
        LOGGER.info("Loaded {} streak milestones.", milestones.size());
    }

    public void reload() {
        loadMilestones();
    }

    /**
     * Process a consecutive login — increment streak.
     */
    public void incrementStreak(@Nonnull PlayerRewardData data) {
        if (!config.getStreak().isEnabled()) return;
        data.incrementStreak();
        LOGGER.debug("Streak incremented for {}: now {}", data.getPlayerUuid(), data.getStreak());
    }

    /**
     * Handle a streak break — reset or partially preserve.
     *
     * @param absenceDays number of days missed
     */
    public void handleStreakBreak(@Nonnull PlayerRewardData data, int absenceDays) {
        if (!config.getStreak().isEnabled()) return;

        int oldStreak = data.getStreak();
        if (oldStreak == 0) return;

        if (config.getStreak().isPartialResetOnBreak()) {
            int divisor = Math.max(1, config.getStreak().getPartialResetDivisor());
            int newStreak = oldStreak / divisor;
            data.setStreak(newStreak);
            LOGGER.info("Streak partially reset for {}: {} -> {} (absent {} days)",
                    data.getPlayerUuid(), oldStreak, newStreak, absenceDays);
        } else {
            data.setStreak(0);
            LOGGER.info("Streak fully reset for {} (absent {} days)", data.getPlayerUuid(), absenceDays);
        }
    }

    /**
     * Calculate the current streak reward multiplier.
     * Formula: base + (streak * perDay), capped at max.
     */
    public double calculateMultiplier(int streak) {
        if (!config.getStreak().isEnabled() || streak <= 0) return 1.0;

        RewardsConfig.StreakSection s = config.getStreak();
        double mult = s.getBaseStreakMultiplier() + (streak * s.getMultiplierPerDay());
        return Math.min(mult, s.getMaxMultiplier());
    }

    /**
     * Check if the player just hit a milestone at their current streak.
     *
     * @return the milestone reached, or null
     */
    @Nullable
    public StreakMilestone checkMilestone(int streak) {
        for (StreakMilestone ms : milestones) {
            if (ms.getDays() == streak) return ms;
        }
        return null;
    }

    /**
     * Get the next upcoming milestone for the player.
     */
    @Nullable
    public StreakMilestone getNextMilestone(int currentStreak) {
        for (StreakMilestone ms : milestones) {
            if (ms.getDays() > currentStreak) return ms;
        }
        return null;
    }

    /**
     * Get all milestones.
     */
    @Nonnull
    public List<StreakMilestone> getAllMilestones() {
        return Collections.unmodifiableList(milestones);
    }

    public boolean isEnabled() {
        return config.getStreak().isEnabled();
    }
}
