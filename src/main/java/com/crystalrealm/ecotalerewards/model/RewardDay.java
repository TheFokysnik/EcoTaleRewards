package com.crystalrealm.ecotalerewards.model;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Configurable reward for a single calendar day.
 * Immutable value object — created from config.
 */
public final class RewardDay {

    private final int day;
    private final BigDecimal coins;
    private final int xp;
    private final List<String> items;
    private final List<String> commands;
    private final String description;

    public RewardDay(int day, BigDecimal coins, int xp,
                     @Nonnull List<String> items,
                     @Nonnull List<String> commands,
                     @Nonnull String description) {
        this.day = day;
        this.coins = coins;
        this.xp = xp;
        this.items = List.copyOf(items);
        this.commands = List.copyOf(commands);
        this.description = description;
    }

    public int getDay() { return day; }
    public BigDecimal getCoins() { return coins; }
    public int getXp() { return xp; }
    @Nonnull public List<String> getItems() { return items; }
    @Nonnull public List<String> getCommands() { return commands; }
    @Nonnull public String getDescription() { return description; }

    @Override
    public String toString() {
        return "RewardDay{day=" + day + ", coins=" + coins + ", xp=" + xp + "}";
    }
}
