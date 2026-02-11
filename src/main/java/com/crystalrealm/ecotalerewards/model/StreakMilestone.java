package com.crystalrealm.ecotalerewards.model;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.List;

/**
 * Streak milestone bonus — awarded when player reaches a specific streak length.
 */
public final class StreakMilestone {

    private final int days;
    private final BigDecimal bonusCoins;
    private final int bonusXP;
    private final double rewardMultiplier;
    private final List<String> commands;
    private final String description;

    public StreakMilestone(int days, BigDecimal bonusCoins, int bonusXP,
                          double rewardMultiplier,
                          @Nonnull List<String> commands,
                          @Nonnull String description) {
        this.days = days;
        this.bonusCoins = bonusCoins;
        this.bonusXP = bonusXP;
        this.rewardMultiplier = rewardMultiplier;
        this.commands = List.copyOf(commands);
        this.description = description;
    }

    public int getDays() { return days; }
    public BigDecimal getBonusCoins() { return bonusCoins; }
    public int getBonusXP() { return bonusXP; }
    public double getRewardMultiplier() { return rewardMultiplier; }
    @Nonnull public List<String> getCommands() { return commands; }
    @Nonnull public String getDescription() { return description; }
}
