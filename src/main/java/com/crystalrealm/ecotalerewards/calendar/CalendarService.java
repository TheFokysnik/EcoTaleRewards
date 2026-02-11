package com.crystalrealm.ecotalerewards.calendar;

import com.crystalrealm.ecotalerewards.config.RewardsConfig;
import com.crystalrealm.ecotalerewards.model.DayStatus;
import com.crystalrealm.ecotalerewards.model.PlayerRewardData;
import com.crystalrealm.ecotalerewards.model.RewardDay;
import com.crystalrealm.ecotalerewards.util.PluginLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Calendar service — manages the daily login calendar logic.
 * Handles strict/soft modes, grace days, and day advancement.
 */
public class CalendarService {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final RewardsConfig config;
    private final Map<Integer, RewardDay> rewardDays = new LinkedHashMap<>();

    public CalendarService(@Nonnull RewardsConfig config) {
        this.config = config;
        loadRewardDays();
    }

    /**
     * Parse reward day configs into RewardDay objects.
     */
    private void loadRewardDays() {
        rewardDays.clear();
        Map<String, RewardsConfig.DayRewardEntry> days = config.getCalendar().getDays();
        for (Map.Entry<String, RewardsConfig.DayRewardEntry> entry : days.entrySet()) {
            try {
                int dayNum = Integer.parseInt(entry.getKey());
                RewardsConfig.DayRewardEntry e = entry.getValue();
                rewardDays.put(dayNum, new RewardDay(
                        dayNum,
                        BigDecimal.valueOf(e.getCoins()),
                        e.getXP(),
                        e.getItems() != null ? e.getItems() : List.of(),
                        e.getCommands() != null ? e.getCommands() : List.of(),
                        e.getDescription() != null ? e.getDescription() : ""
                ));
            } catch (NumberFormatException ignored) {
                LOGGER.warn("Invalid day key in config: {}", entry.getKey());
            }
        }
        LOGGER.info("Loaded {} calendar reward days.", rewardDays.size());
    }

    /**
     * Reload reward days from config (after hot-reload).
     */
    public void reload() {
        loadRewardDays();
    }

    /**
     * Process a player login — update their calendar state.
     * Called when a player joins the server.
     *
     * @return number of absence days (0 = consecutive login)
     */
    public int processLogin(@Nonnull PlayerRewardData data, @Nonnull LocalDate today) {
        LocalDate lastLogin = data.getLastLoginDate();
        data.setLastLoginDate(today);

        if (lastLogin == null) {
            // First ever login
            data.setCurrentDay(1);
            return 0;
        }

        if (lastLogin.equals(today)) {
            // Already logged in today
            return 0;
        }

        long daysSinceLastLogin = ChronoUnit.DAYS.between(lastLogin, today);

        if (daysSinceLastLogin == 1) {
            // Consecutive login — advance calendar
            advanceDay(data);
            return 0;
        }

        // Player missed one or more days
        int absenceDays = (int) daysSinceLastLogin - 1;

        if (config.getCalendar().isStrictMode()) {
            // Strict mode: any miss resets calendar
            data.resetCalendar();
            LOGGER.debug("Strict mode: calendar reset for {} after {} missed day(s)", data.getPlayerUuid(), absenceDays);
        } else {
            int graceDays = config.getCalendar().getGraceDays();
            if (absenceDays <= graceDays) {
                // Within grace period — just advance to next day
                advanceDay(data);
                LOGGER.debug("Grace period: {} missed {} day(s), within grace ({})", data.getPlayerUuid(), absenceDays, graceDays);
            } else {
                // Beyond grace — reset if configured, otherwise just advance
                if (config.getCalendar().isResetOnExpiry()) {
                    data.resetCalendar();
                    LOGGER.debug("Calendar reset for {} — missed {} day(s), grace was {}", data.getPlayerUuid(), absenceDays, graceDays);
                } else {
                    advanceDay(data);
                }
            }
        }

        return absenceDays;
    }

    /**
     * Advance caller to the next calendar day. Wraps around if past total days.
     */
    private void advanceDay(PlayerRewardData data) {
        int totalDays = config.getCalendar().getTotalDays();
        int next = data.getCurrentDay() + 1;
        if (next > totalDays) {
            if (config.getCalendar().isResetOnExpiry()) {
                data.resetCalendar();
            }
            // else: stay at max day (calendar complete)
        } else {
            data.setCurrentDay(next);
        }
    }

    /**
     * Get the status of each calendar day for display in GUI.
     */
    @Nonnull
    public Map<Integer, DayStatus> getDayStatuses(@Nonnull PlayerRewardData data) {
        int totalDays = config.getCalendar().getTotalDays();
        int currentDay = data.getCurrentDay();
        Set<Integer> claimed = data.getClaimedDays();

        Map<Integer, DayStatus> statuses = new LinkedHashMap<>();
        for (int day = 1; day <= totalDays; day++) {
            if (claimed.contains(day)) {
                statuses.put(day, DayStatus.CLAIMED);
            } else if (day == currentDay) {
                statuses.put(day, DayStatus.AVAILABLE);
            } else if (day < currentDay) {
                statuses.put(day, DayStatus.MISSED);
            } else {
                statuses.put(day, DayStatus.LOCKED);
            }
        }
        return statuses;
    }

    /**
     * Get reward for a specific day.
     */
    @Nullable
    public RewardDay getRewardForDay(int day) {
        return rewardDays.get(day);
    }

    /**
     * Get total configured days.
     */
    public int getTotalDays() {
        return config.getCalendar().getTotalDays();
    }

    /**
     * Check if claim is available for the player's current day.
     */
    public boolean canClaim(@Nonnull PlayerRewardData data, @Nonnull LocalDate today) {
        int currentDay = data.getCurrentDay();

        // Already claimed this day
        if (data.isClaimedDay(currentDay)) return false;

        // Already claimed today (different day number check)
        if (today.equals(data.getLastClaimDate())) return false;

        return true;
    }

    /**
     * Mark a day as claimed and record the claim date.
     */
    public void markClaimed(@Nonnull PlayerRewardData data, int day, @Nonnull LocalDate today) {
        data.addClaimedDay(day);
        data.setLastClaimDate(today);
        data.recordClaimToday(today);
    }
}
