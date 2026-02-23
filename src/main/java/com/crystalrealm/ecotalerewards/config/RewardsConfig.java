package com.crystalrealm.ecotalerewards.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POJO model for EcoTaleRewards configuration (deserialized from JSON).
 * Field names match JSON keys exactly for Gson auto-mapping.
 */
public class RewardsConfig {

    private GeneralSection General = new GeneralSection();
    private CalendarSection Calendar = new CalendarSection();
    private StreakSection Streak = new StreakSection();
    private ReturnRewardsSection ReturnRewards = new ReturnRewardsSection();
    private AntiAbuseSection AntiAbuse = new AntiAbuseSection();
    private List<VipTier> VipTiers = new ArrayList<>();
    private GenericEconomySection GenericEconomy;
    private GenericLevelingSection GenericLeveling;
    private MMOSkillTreeSection MMOSkillTree;

    // ── Getters ─────────────────────────────────────────────────

    public GeneralSection getGeneral() { return General; }
    public CalendarSection getCalendar() { return Calendar; }
    public StreakSection getStreak() { return Streak; }
    public ReturnRewardsSection getReturnRewards() { return ReturnRewards; }
    public AntiAbuseSection getAntiAbuse() { return AntiAbuse; }
    public List<VipTier> getVipTiers() { return VipTiers; }
    public GenericEconomySection getGenericEconomy() {
        return GenericEconomy != null ? GenericEconomy : new GenericEconomySection();
    }
    public GenericLevelingSection getGenericLeveling() {
        return GenericLeveling != null ? GenericLeveling : new GenericLevelingSection();
    }
    public MMOSkillTreeSection getMMOSkillTree() {
        return MMOSkillTree != null ? MMOSkillTree : new MMOSkillTreeSection();
    }

    // ═════════════════════════════════════════════════════════
    //  SECTION CLASSES
    // ═════════════════════════════════════════════════════════

    public static class GeneralSection {
        private boolean DebugMode = false;
        private String Language = "ru";
        private String MessagePrefix = "<dark_gray>[<gold>\uD83C\uDF81<dark_gray>]";
        private int AutoSaveIntervalMinutes = 5;
        private String EconomyProvider = "ecotale";
        private String LevelProvider = "rpgleveling";

        public boolean isDebugMode() { return DebugMode; }
        public void setDebugMode(boolean v) { DebugMode = v; }
        public String getLanguage() { return Language; }
        public void setLanguage(String v) { Language = v; }
        public String getMessagePrefix() { return MessagePrefix; }
        public int getAutoSaveIntervalMinutes() { return AutoSaveIntervalMinutes; }
        public void setAutoSaveIntervalMinutes(int v) { AutoSaveIntervalMinutes = Math.max(1, v); }
        public String getEconomyProvider() { return EconomyProvider != null ? EconomyProvider : "ecotale"; }
        public String getLevelProvider() { return LevelProvider != null ? LevelProvider : "rpgleveling"; }
    }

    public static class CalendarSection {
        private int TotalDays = 30;
        private boolean StrictMode = false;
        private int GraceDays = 2;
        private boolean ResetOnExpiry = true;
        private String DailyResetTime = "00:00";
        private Map<String, DayRewardEntry> Days = new LinkedHashMap<>();

        public int getTotalDays() { return TotalDays; }
        public void setTotalDays(int v) { TotalDays = Math.max(1, v); }
        public boolean isStrictMode() { return StrictMode; }
        public void setStrictMode(boolean v) { StrictMode = v; }
        public int getGraceDays() { return GraceDays; }
        public void setGraceDays(int v) { GraceDays = Math.max(0, v); }
        public boolean isResetOnExpiry() { return ResetOnExpiry; }
        public String getDailyResetTime() { return DailyResetTime; }
        public Map<String, DayRewardEntry> getDays() { return Days; }
    }

    public static class DayRewardEntry {
        private double Coins = 0.0;
        private int XP = 0;
        private List<String> Items = new ArrayList<>();
        private List<String> Commands = new ArrayList<>();
        private String Description = "";

        public double getCoins() { return Coins; }
        public void setCoins(double v) { Coins = Math.max(0, v); }
        public int getXP() { return XP; }
        public void setXP(int v) { XP = Math.max(0, v); }
        public List<String> getItems() { return Items; }
        public void setItems(List<String> v) { Items = v != null ? v : new ArrayList<>(); }
        public List<String> getCommands() { return Commands; }
        public void setCommands(List<String> v) { Commands = v != null ? v : new ArrayList<>(); }
        public String getDescription() { return Description; }
        public void setDescription(String v) { Description = v != null ? v : ""; }
    }

    public static class StreakSection {
        private boolean Enabled = true;
        private boolean PartialResetOnBreak = true;
        private int PartialResetDivisor = 2;
        private Map<String, MilestoneEntry> Milestones = new LinkedHashMap<>();
        private double BaseStreakMultiplier = 1.0;
        private double MultiplierPerDay = 0.02;
        private double MaxMultiplier = 3.0;

        public boolean isEnabled() { return Enabled; }
        public void setEnabled(boolean v) { Enabled = v; }
        public boolean isPartialResetOnBreak() { return PartialResetOnBreak; }
        public int getPartialResetDivisor() { return PartialResetDivisor; }
        public Map<String, MilestoneEntry> getMilestones() { return Milestones; }
        public double getBaseStreakMultiplier() { return BaseStreakMultiplier; }
        public double getMultiplierPerDay() { return MultiplierPerDay; }
        public double getMaxMultiplier() { return MaxMultiplier; }
    }

    public static class MilestoneEntry {
        private double BonusCoins = 0.0;
        private int BonusXP = 0;
        private double RewardMultiplier = 1.0;
        private List<String> Commands = new ArrayList<>();
        private String Description = "";

        public double getBonusCoins() { return BonusCoins; }
        public int getBonusXP() { return BonusXP; }
        public double getRewardMultiplier() { return RewardMultiplier; }
        public List<String> getCommands() { return Commands; }
        public String getDescription() { return Description; }
    }

    public static class ReturnRewardsSection {
        private boolean Enabled = true;
        private List<ReturnTierEntry> Tiers = new ArrayList<>();

        public boolean isEnabled() { return Enabled; }
        public void setEnabled(boolean v) { Enabled = v; }
        public List<ReturnTierEntry> getTiers() { return Tiers; }
    }

    public static class ReturnTierEntry {
        private int MinAbsenceDays = 7;
        private int MaxAbsenceDays = 9999;
        private double Coins = 0.0;
        private int XP = 0;
        private List<String> Items = new ArrayList<>();
        private List<String> Commands = new ArrayList<>();
        private String Description = "";

        public int getMinAbsenceDays() { return MinAbsenceDays; }
        public int getMaxAbsenceDays() { return MaxAbsenceDays; }
        public double getCoins() { return Coins; }
        public int getXP() { return XP; }
        public List<String> getItems() { return Items; }
        public List<String> getCommands() { return Commands; }
        public String getDescription() { return Description; }
    }

    public static class AntiAbuseSection {
        private int MinOnlineMinutes = 5;
        private int RelogCooldownMinutes = 60;
        private int MaxClaimsPerDay = 1;
        private boolean LogAllRewards = true;

        public int getMinOnlineMinutes() { return MinOnlineMinutes; }
        public void setMinOnlineMinutes(int v) { MinOnlineMinutes = Math.max(0, v); }
        public int getRelogCooldownMinutes() { return RelogCooldownMinutes; }
        public int getMaxClaimsPerDay() { return MaxClaimsPerDay; }
        public boolean isLogAllRewards() { return LogAllRewards; }
    }

    public static class VipTier {
        private String Permission = "";
        private double Multiplier = 1.0;
        private String DisplayName = "";

        public String getPermission() { return Permission; }
        public double getMultiplier() { return Multiplier; }
        public String getDisplayName() { return DisplayName; }
    }

    /** Config section for a generic economy plugin adapter. */
    public static class GenericEconomySection {
        private String ClassName = "";
        private String InstanceMethod = "";
        private String DepositMethod = "deposit";
        private boolean DepositHasReason = false;

        public String getClassName() { return ClassName; }
        public String getInstanceMethod() { return InstanceMethod; }
        public String getDepositMethod() { return DepositMethod; }
        public boolean isDepositHasReason() { return DepositHasReason; }

        public boolean isConfigured() {
            return ClassName != null && !ClassName.isBlank();
        }
    }

    /** Config section for a generic leveling plugin adapter. */
    public static class GenericLevelingSection {
        private String ClassName = "";
        private String InstanceMethod = "";
        private String GrantXPMethod = "addXP";

        public String getClassName() { return ClassName; }
        public String getInstanceMethod() { return InstanceMethod; }
        public String getGrantXPMethod() { return GrantXPMethod; }

        public boolean isConfigured() {
            return ClassName != null && !ClassName.isBlank();
        }
    }

    /** Config section for MMOSkillTree-specific settings. */
    public static class MMOSkillTreeSection {
        private String DefaultSkillType = "SWORDS";

        public String getDefaultSkillType() { return DefaultSkillType != null ? DefaultSkillType : "SWORDS"; }
    }
}
