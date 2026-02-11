package com.crystalrealm.ecotalerewards.model;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.List;

/**
 * Return reward tier — given to players returning after X days of absence.
 */
public final class ReturnRewardTier {

    private final int minAbsenceDays;
    private final int maxAbsenceDays;
    private final BigDecimal coins;
    private final int xp;
    private final List<String> items;
    private final List<String> commands;
    private final String description;

    public ReturnRewardTier(int minAbsenceDays, int maxAbsenceDays,
                            BigDecimal coins, int xp,
                            @Nonnull List<String> items,
                            @Nonnull List<String> commands,
                            @Nonnull String description) {
        this.minAbsenceDays = minAbsenceDays;
        this.maxAbsenceDays = maxAbsenceDays;
        this.coins = coins;
        this.xp = xp;
        this.items = List.copyOf(items);
        this.commands = List.copyOf(commands);
        this.description = description;
    }

    public int getMinAbsenceDays() { return minAbsenceDays; }
    public int getMaxAbsenceDays() { return maxAbsenceDays; }
    public BigDecimal getCoins() { return coins; }
    public int getXp() { return xp; }
    @Nonnull public List<String> getItems() { return items; }
    @Nonnull public List<String> getCommands() { return commands; }
    @Nonnull public String getDescription() { return description; }

    /**
     * Check if this tier matches the given absence duration.
     */
    public boolean matches(int absenceDays) {
        return absenceDays >= minAbsenceDays && absenceDays <= maxAbsenceDays;
    }
}
