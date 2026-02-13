package com.crystalrealm.ecotalerewards.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.*;

/**
 * Per-player reward data — calendar progress, streak, and timestamps.
 * Mutable — updated by services, persisted via DAO.
 */
public final class PlayerRewardData {

    private final UUID playerUuid;

    /** Current logical day in the calendar (1-based). */
    private int currentDay;

    /** Current consecutive login streak (days). */
    private int streak;

    /** Longest streak ever achieved. */
    private int longestStreak;

    /** Date of last login (server date). */
    @Nullable
    private LocalDate lastLoginDate;

    /** Date of last reward claim. */
    @Nullable
    private LocalDate lastClaimDate;

    /** Total rewards claimed all-time. */
    private int totalClaimed;

    /** Set of calendar days that have been claimed. */
    private final Set<Integer> claimedDays;

    /** Whether the player has a pending return reward (not yet shown). */
    private boolean pendingReturnReward;

    /** Absence days count (for return reward calculation). */
    private int absenceDays;

    /** Session join time (epoch millis, transient — not persisted). */
    private transient long sessionJoinTime;

    /** Number of claims today (anti-abuse). */
    private transient int claimsToday;

    /** Date of claimsToday counter (anti-abuse). */
    @Nullable
    private transient LocalDate claimsTodayDate;

    /** Date when the calendar GUI was last auto-shown (once per server-day). */
    @Nullable
    private transient LocalDate lastAutoGuiDate;

    public PlayerRewardData(@Nonnull UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.currentDay = 1;
        this.streak = 0;
        this.longestStreak = 0;
        this.totalClaimed = 0;
        this.claimedDays = new LinkedHashSet<>();
        this.pendingReturnReward = false;
        this.absenceDays = 0;
    }

    // ── Getters ─────────────────────────────────────────────

    @Nonnull public UUID getPlayerUuid() { return playerUuid; }
    public int getCurrentDay() { return currentDay; }
    public int getStreak() { return streak; }
    public int getLongestStreak() { return longestStreak; }
    @Nullable public LocalDate getLastLoginDate() { return lastLoginDate; }
    @Nullable public LocalDate getLastClaimDate() { return lastClaimDate; }
    public int getTotalClaimed() { return totalClaimed; }
    @Nonnull public Set<Integer> getClaimedDays() { return Collections.unmodifiableSet(claimedDays); }
    public boolean isPendingReturnReward() { return pendingReturnReward; }
    public int getAbsenceDays() { return absenceDays; }
    public long getSessionJoinTime() { return sessionJoinTime; }
    public int getClaimsToday() { return claimsToday; }
    @Nullable public LocalDate getClaimsTodayDate() { return claimsTodayDate; }

    /**
     * Returns true if the auto-GUI has already been shown today.
     */
    public boolean hasAutoGuiShownToday() {
        return LocalDate.now().equals(lastAutoGuiDate);
    }

    /**
     * Mark that the auto-GUI was shown today.
     */
    public void markAutoGuiShown() {
        this.lastAutoGuiDate = LocalDate.now();
    }

    // ── Setters ─────────────────────────────────────────────

    public void setCurrentDay(int currentDay) { this.currentDay = currentDay; }

    public void setStreak(int streak) {
        this.streak = streak;
        if (streak > longestStreak) this.longestStreak = streak;
    }

    public void setLongestStreak(int longestStreak) { this.longestStreak = longestStreak; }
    public void setLastLoginDate(@Nullable LocalDate lastLoginDate) { this.lastLoginDate = lastLoginDate; }
    public void setLastClaimDate(@Nullable LocalDate lastClaimDate) { this.lastClaimDate = lastClaimDate; }
    public void setTotalClaimed(int totalClaimed) { this.totalClaimed = totalClaimed; }
    public void setPendingReturnReward(boolean pending) { this.pendingReturnReward = pending; }
    public void setAbsenceDays(int absenceDays) { this.absenceDays = absenceDays; }
    public void setSessionJoinTime(long sessionJoinTime) { this.sessionJoinTime = sessionJoinTime; }

    public void addClaimedDay(int day) {
        claimedDays.add(day);
        totalClaimed++;
    }

    public boolean isClaimedDay(int day) {
        return claimedDays.contains(day);
    }

    public void incrementStreak() {
        streak++;
        if (streak > longestStreak) longestStreak = streak;
    }

    /**
     * Increment claims-today counter with date check.
     */
    public void recordClaimToday(@Nonnull LocalDate today) {
        if (!today.equals(claimsTodayDate)) {
            claimsTodayDate = today;
            claimsToday = 0;
        }
        claimsToday++;
    }

    public int getClaimsToday(@Nonnull LocalDate today) {
        if (!today.equals(claimsTodayDate)) return 0;
        return claimsToday;
    }

    /**
     * Reset the calendar to day 1 (full reset).
     */
    public void resetCalendar() {
        currentDay = 1;
        claimedDays.clear();
    }

    @Override
    public String toString() {
        return "PlayerRewardData{uuid=" + playerUuid +
                ", day=" + currentDay +
                ", streak=" + streak +
                ", claimed=" + totalClaimed + "}";
    }
}
