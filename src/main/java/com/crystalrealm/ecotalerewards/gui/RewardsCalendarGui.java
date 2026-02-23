package com.crystalrealm.ecotalerewards.gui;

import com.crystalrealm.ecotalerewards.calendar.CalendarService;
import com.crystalrealm.ecotalerewards.config.RewardsConfig;
import com.crystalrealm.ecotalerewards.lang.LangManager;
import com.crystalrealm.ecotalerewards.model.*;
import com.crystalrealm.ecotalerewards.protection.AntiAbuseGuard;
import com.crystalrealm.ecotalerewards.returns.ReturnRewardService;
import com.crystalrealm.ecotalerewards.rewards.RewardService;
import com.crystalrealm.ecotalerewards.storage.RewardStorage;
import com.crystalrealm.ecotalerewards.streaks.StreakService;
import com.crystalrealm.ecotalerewards.util.MessageUtil;
import com.crystalrealm.ecotalerewards.util.PluginLogger;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Player-facing daily rewards calendar GUI.
 *
 * <p>Layout:</p>
 * <ul>
 *   <li>6×5 calendar grid (C1–C30) with colour-coded day status</li>
 *   <li>Streak info panel (current streak, multiplier, next milestone)</li>
 *   <li>Return-reward banner with claim button (if pending)</li>
 *   <li>Central Claim button for the AVAILABLE day</li>
 * </ul>
 *
 * <p>All event handling follows the slot-based pattern: events are bound once
 * in {@link #build}, dispatched in {@link #handleDataEvent}, and the page
 * is refreshed via {@link #sendUpdate(UICommandBuilder)} without reloading.</p>
 *
 * @version 1.0.0
 */
public final class RewardsCalendarGui extends InteractiveCustomUIPage<RewardsCalendarGui.CalendarEventData> {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private static final String PAGE_PATH = "Pages/CrystalRealm_EcoTaleRewards_CalendarPanel.ui";
    private static final int    MAX_DAYS  = 30;

    // ── Event data keys ─────────────────────────────────────
    private static final String KEY_ACTION = "Action";
    private static final String KEY_SLOT   = "Slot";

    static final BuilderCodec<CalendarEventData> CODEC = ReflectiveCodecBuilder
            .<CalendarEventData>create(CalendarEventData.class, CalendarEventData::new)
            .addStringField(KEY_ACTION, (d, v) -> d.action = v, d -> d.action)
            .addStringField(KEY_SLOT,   (d, v) -> d.slot = v,   d -> d.slot)
            .build();

    // ── Dependencies (injected via constructor) ─────────────
    private final CalendarService    calendarService;
    private final StreakService      streakService;
    private final ReturnRewardService returnService;
    private final RewardService      rewardService;
    private final AntiAbuseGuard     antiAbuse;
    private final RewardStorage      storage;
    private final LangManager        lang;
    private final RewardsConfig      config;
    private final UUID               playerUuid;

    @Nullable private final String errorMessage;
    @Nullable private final String successMessage;

    // Saved for re-open
    private Ref<EntityStore>   savedRef;
    private Store<EntityStore> savedStore;

    // ════════════════════════════════════════════════════════
    //  CONSTRUCTORS
    // ════════════════════════════════════════════════════════

    public RewardsCalendarGui(@Nonnull CalendarService calendarService,
                              @Nonnull StreakService streakService,
                              @Nonnull ReturnRewardService returnService,
                              @Nonnull RewardService rewardService,
                              @Nonnull AntiAbuseGuard antiAbuse,
                              @Nonnull RewardStorage storage,
                              @Nonnull LangManager lang,
                              @Nonnull RewardsConfig config,
                              @Nonnull PlayerRef playerRef,
                              @Nonnull UUID playerUuid) {
        this(calendarService, streakService, returnService, rewardService,
             antiAbuse, storage, lang, config, playerRef, playerUuid, null, null);
    }

    public RewardsCalendarGui(@Nonnull CalendarService calendarService,
                              @Nonnull StreakService streakService,
                              @Nonnull ReturnRewardService returnService,
                              @Nonnull RewardService rewardService,
                              @Nonnull AntiAbuseGuard antiAbuse,
                              @Nonnull RewardStorage storage,
                              @Nonnull LangManager lang,
                              @Nonnull RewardsConfig config,
                              @Nonnull PlayerRef playerRef,
                              @Nonnull UUID playerUuid,
                              @Nullable String errorMessage,
                              @Nullable String successMessage) {
        super(playerRef, CustomPageLifetime.CanDismiss, CODEC);
        this.calendarService = calendarService;
        this.streakService   = streakService;
        this.returnService   = returnService;
        this.rewardService   = rewardService;
        this.antiAbuse       = antiAbuse;
        this.storage         = storage;
        this.lang            = lang;
        this.config          = config;
        this.playerUuid      = playerUuid;
        this.errorMessage    = errorMessage;
        this.successMessage  = successMessage;
    }

    // ════════════════════════════════════════════════════════
    //  BUILD
    // ════════════════════════════════════════════════════════

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        this.savedRef   = ref;
        this.savedStore = store;

        cmd.append(PAGE_PATH);

        // Title
        cmd.set("#TitleLabel.Text", L("gui.title"));

        // ── Bind events (once) ──────────────────────────────

        // Per-day claim buttons (Day1Btn..Day30Btn)
        for (int i = 1; i <= MAX_DAYS; i++) {
            events.addEventBinding(CustomUIEventBindingType.Activating, "#Day" + i + "Btn",
                    new EventData().append(KEY_ACTION, "day_claim").append(KEY_SLOT, String.valueOf(i)));
        }

        // Return reward claim
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ReturnClaimBtn",
                new EventData().append(KEY_ACTION, "claim_return"));

        // ── Banners ─────────────────────────────────────────
        if (errorMessage != null && !errorMessage.isEmpty()) {
            cmd.set("#ErrorBanner.Visible", true);
            cmd.set("#ErrorText.Text", stripForUI(errorMessage));
        }
        if (successMessage != null && !successMessage.isEmpty()) {
            cmd.set("#SuccessBanner.Visible", true);
            cmd.set("#SuccessText.Text", stripForUI(successMessage));
        }

        // ── Fill all data ───────────────────────────────────
        updateCalendarData(cmd);
        updateStreakPanel(cmd);
        updateReturnBanner(cmd);
        updateClaimArea(cmd);

        LOGGER.info("Calendar GUI built for {}", playerUuid);
    }

    // ════════════════════════════════════════════════════════
    //  HANDLE EVENTS
    // ════════════════════════════════════════════════════════

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull CalendarEventData data) {
        switch (data.action) {
            case "day_claim"    -> handleDayClaim(data.slot);
            case "claim_return" -> handleReturnClaim();
            case "day_info"     -> handleDayInfo(data.slot);
        }
    }

    // ── Day cell click → claim if available ─────────────────

    private void handleDayClaim(String slotStr) {
        int day = parseSlot(slotStr);
        if (day < 1 || day > MAX_DAYS) return;

        PlayerRewardData prd = storage.loadOrCreate(playerUuid);
        if (day != prd.getCurrentDay()) {
            // Clicked on a non-current day — show info
            handleDayInfo(slotStr);
            return;
        }
        handleClaim();
    }

    // ── Claim daily reward ──────────────────────────────────

    private void handleClaim() {
        PlayerRewardData prd = storage.loadOrCreate(playerUuid);
        LocalDate today = LocalDate.now();

        // Anti-abuse check
        String abuseReason = antiAbuse.checkCanClaim(prd, today);
        if (abuseReason != null) {
            String msg = switch (abuseReason) {
                case "min_online"  -> L("error.min_online",
                        "minutes", String.valueOf(config.getAntiAbuse().getMinOnlineMinutes()));
                case "cooldown"    -> L("error.cooldown");
                case "max_claims"  -> L("error.max_claims");
                default            -> L("error.generic");
            };
            refreshPage(msg, null);
            return;
        }

        // Calendar check
        if (!calendarService.canClaim(prd, today)) {
            refreshPage(L("error.already_claimed"), null);
            return;
        }

        int day = prd.getCurrentDay();
        RewardDay rewardDay = calendarService.getRewardForDay(day);
        if (rewardDay == null) {
            refreshPage(L("error.no_reward"), null);
            return;
        }

        // Increment streak
        streakService.incrementStreak(prd);
        double streakMult = streakService.calculateMultiplier(prd.getStreak());
        double vipMult = rewardService.getVipMultiplier(playerUuid, null);

        // Register ECS context so RewardService can access Player entity for inventory
        rewardService.registerPlayerContext(savedRef, savedStore);
        rewardService.issueDayReward(playerUuid, rewardDay, vipMult, streakMult);

        // Mark claimed
        calendarService.markClaimed(prd, day, today);
        antiAbuse.recordClaim(playerUuid);

        // Check milestone
        StreakMilestone milestone = streakService.checkMilestone(prd.getStreak());
        if (milestone != null) {
            rewardService.issueMilestoneReward(playerUuid, milestone, vipMult);
        }

        // Save
        storage.savePlayer(playerUuid);

        // Build success message
        String coinStr = MessageUtil.formatCoins(rewardDay.getCoins());
        String msg = L("success.claimed", "day", String.valueOf(day),
                "coins", coinStr, "xp", String.valueOf(rewardDay.getXp()));
        if (!rewardDay.getItems().isEmpty()) {
            msg += " + " + formatItemsShort(rewardDay.getItems());
        }

        if (milestone != null) {
            msg += " " + L("success.milestone",
                    "days", String.valueOf(milestone.getDays()),
                    "bonus", MessageUtil.formatCoins(milestone.getBonusCoins()));
        }

        refreshPage(null, msg);
    }

    // ── Claim return reward ─────────────────────────────────

    private void handleReturnClaim() {
        PlayerRewardData prd = storage.loadOrCreate(playerUuid);

        ReturnRewardTier tier = returnService.getPlayerReturnTier(prd);
        if (tier == null) {
            refreshPage(L("error.no_return_reward"), null);
            return;
        }

        double vipMult = rewardService.getVipMultiplier(playerUuid, null);
        rewardService.registerPlayerContext(savedRef, savedStore);
        rewardService.issueReturnReward(playerUuid, tier, vipMult);
        returnService.markReturnClaimed(prd);
        storage.savePlayer(playerUuid);

        String msg = L("success.return_claimed",
                "coins", MessageUtil.formatCoins(tier.getCoins()),
                "xp", String.valueOf(tier.getXp()));
        refreshPage(null, msg);
    }

    // ── Day info (click on cell) ────────────────────────────

    private void handleDayInfo(String slotStr) {
        int day = parseSlot(slotStr);
        if (day < 1 || day > MAX_DAYS) return;

        RewardDay rewardDay = calendarService.getRewardForDay(day);
        if (rewardDay == null) return;

        PlayerRewardData prd = storage.loadOrCreate(playerUuid);
        Map<Integer, DayStatus> statuses = calendarService.getDayStatuses(prd);
        DayStatus status = statuses.getOrDefault(day, DayStatus.LOCKED);

        String sText = L("day.status." + status.getId());
        String info = L("gui.day_info",
                "day", String.valueOf(day),
                "coins", MessageUtil.formatCoins(rewardDay.getCoins()),
                "xp", String.valueOf(rewardDay.getXp()),
                "status", sText);
        if (!rewardDay.getItems().isEmpty()) {
            info += " | " + formatItemsShort(rewardDay.getItems());
        }

        try {
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#SuccessBanner.Visible", true);
            cmd.set("#SuccessText.Text", stripForUI(info));
            cmd.set("#ErrorBanner.Visible", false);
            sendUpdate(cmd);
        } catch (Exception e) {
            LOGGER.warn("[dayInfo] sendUpdate failed: {}", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════
    //  REFRESH PAGE (sendUpdate — no Loading!)
    // ════════════════════════════════════════════════════════

    private void refreshPage(@Nullable String error, @Nullable String success) {
        try {
            UICommandBuilder cmd = new UICommandBuilder();

            // Banners
            cmd.set("#ErrorBanner.Visible", error != null && !error.isEmpty());
            if (error != null && !error.isEmpty()) cmd.set("#ErrorText.Text", stripForUI(error));
            cmd.set("#SuccessBanner.Visible", success != null && !success.isEmpty());
            if (success != null && !success.isEmpty()) cmd.set("#SuccessText.Text", stripForUI(success));

            // Refresh all data
            updateCalendarData(cmd);
            updateStreakPanel(cmd);
            updateReturnBanner(cmd);
            updateClaimArea(cmd);

            sendUpdate(cmd);
        } catch (Exception e) {
            LOGGER.warn("[refreshPage] sendUpdate failed, falling back to reopen: {}", e.getMessage());
            reopen(error, success);
        }
    }

    // ════════════════════════════════════════════════════════
    //  DATA BUILDERS
    // ════════════════════════════════════════════════════════

    private void updateCalendarData(@Nonnull UICommandBuilder cmd) {
        PlayerRewardData prd = storage.loadOrCreate(playerUuid);
        Map<Integer, DayStatus> statuses = calendarService.getDayStatuses(prd);
        int totalDays = calendarService.getTotalDays();
        LocalDate today = LocalDate.now();
        boolean canClaim = calendarService.canClaim(prd, today)
                && antiAbuse.checkCanClaim(prd, today) == null;

        cmd.set("#SecCalendar.Text", L("gui.sec.calendar"));

        for (int i = 1; i <= MAX_DAYS; i++) {
            String prefix = "#Day" + i;

            if (i > totalDays) {
                cmd.set(prefix + ".Visible", false);
                continue;
            }

            cmd.set(prefix + ".Visible", true);

            RewardDay rd = calendarService.getRewardForDay(i);
            DayStatus status = statuses.getOrDefault(i, DayStatus.LOCKED);

            // Day label
            cmd.set(prefix + "Day.Text", L("gui.day_label", "day", String.valueOf(i)));

            // Split coins / XP labels
            if (rd != null) {
                cmd.set(prefix + "Coins.Text", "+" + MessageUtil.formatCoins(rd.getCoins()) + "$");
                cmd.set(prefix + "Xp.Text", "+" + rd.getXp() + " XP");
            } else {
                cmd.set(prefix + "Coins.Text", "");
                cmd.set(prefix + "Xp.Text", "");
            }

            // Items — two separate rows
            if (rd != null && !rd.getItems().isEmpty()) {
                // Item 1
                cmd.set(prefix + "Item1.Visible", true);
                String icon1Id = extractItemNameForIcon(rd.getItems().get(0));
                if (icon1Id != null) {
                    cmd.set(prefix + "Icon1.ItemId", icon1Id);
                }
                cmd.set(prefix + "Item1Name.Text", formatSingleItem(rd.getItems().get(0)));

                // Item 2
                if (rd.getItems().size() >= 2) {
                    cmd.set(prefix + "Item2.Visible", true);
                    String icon2Id = extractItemNameForIcon(rd.getItems().get(1));
                    if (icon2Id != null) {
                        cmd.set(prefix + "Icon2.ItemId", icon2Id);
                    }
                    String item2Text = formatSingleItem(rd.getItems().get(1));
                    if (rd.getItems().size() > 2) {
                        item2Text += " +" + (rd.getItems().size() - 2);
                    }
                    cmd.set(prefix + "Item2Name.Text", item2Text);
                } else {
                    cmd.set(prefix + "Item2.Visible", false);
                }
            } else {
                cmd.set(prefix + "Item1.Visible", false);
                cmd.set(prefix + "Item2.Visible", false);
            }

            // Status text + style + dynamic background color based on state
            cmd.set(prefix + "Status.Text", statusText(status));
            cmd.set(prefix + "Status.Style.TextColor", statusColor(status));
            cmd.set(prefix + ".Background.Color", dayBackgroundColor(i, status));

            // Claim button — visible for the available day
            if (status == DayStatus.AVAILABLE) {
                cmd.set(prefix + "Btn.Visible", true);
                cmd.set(prefix + "Btn.Text", canClaim
                        ? L("gui.btn.claim")
                        : L("gui.btn.wait"));
            } else {
                cmd.set(prefix + "Btn.Visible", false);
            }
        }
    }

    private void updateStreakPanel(@Nonnull UICommandBuilder cmd) {
        PlayerRewardData prd = storage.loadOrCreate(playerUuid);

        // Left section: streak + multiplier
        cmd.set("#StreakLabel.Text", L("gui.streak_label"));
        cmd.set("#StreakValue.Text", String.valueOf(prd.getStreak()));

        double streakMult = streakService.calculateMultiplier(prd.getStreak());
        double vipMult = rewardService.getVipMultiplier(playerUuid, null);
        double totalMult = streakMult * vipMult;

        cmd.set("#MultLabel.Text", L("gui.mult_label"));
        String vipName = rewardService.getVipTierName(playerUuid, null);
        if (vipName != null) {
            cmd.set("#MultValue.Text", String.format("x%.2f [%s]", totalMult, vipName));
        } else {
            cmd.set("#MultValue.Text", String.format("x%.2f", totalMult));
        }

        // Center: progress + next milestone
        int totalDays = calendarService.getTotalDays();
        int currentDay = prd.getCurrentDay();
        int claimed = Math.min(currentDay - 1, totalDays);
        if (prd.isClaimedDay(currentDay)) claimed = currentDay;
        cmd.set("#ProgressText.Text", L("gui.progress", "claimed", String.valueOf(claimed), "total", String.valueOf(totalDays)));

        StreakMilestone next = streakService.getNextMilestone(prd.getStreak());
        if (next != null) {
            int remaining = next.getDays() - prd.getStreak();
            cmd.set("#NextMsLabel.Text", L("gui.next_milestone",
                    "days", String.valueOf(next.getDays()),
                    "remaining", String.valueOf(remaining)));
        } else {
            cmd.set("#NextMsLabel.Text", L("gui.all_milestones_done"));
        }

        // Right section: claimed stats
        cmd.set("#TotalCoinsLabel.Text", L("gui.total_claimed"));
        cmd.set("#TotalCoinsValue.Text", String.valueOf(prd.getTotalClaimed()));
        cmd.set("#TotalXpLabel.Text", L("gui.current_day"));
        cmd.set("#TotalXpValue.Text", String.valueOf(prd.getCurrentDay()) + " / " + totalDays);
    }

    private void updateReturnBanner(@Nonnull UICommandBuilder cmd) {
        PlayerRewardData prd = storage.loadOrCreate(playerUuid);
        ReturnRewardTier tier = returnService.getPlayerReturnTier(prd);

        if (tier != null) {
            cmd.set("#ReturnBanner.Visible", true);
            cmd.set("#ReturnText.Text", stripForUI(L("gui.return_reward",
                    "days", String.valueOf(prd.getAbsenceDays()),
                    "coins", MessageUtil.formatCoins(tier.getCoins()),
                    "xp", String.valueOf(tier.getXp()))));
            cmd.set("#ReturnClaimBtn.Text", L("gui.btn.claim"));
        } else {
            cmd.set("#ReturnBanner.Visible", false);
        }
    }

    private void updateClaimArea(@Nonnull UICommandBuilder cmd) {
        PlayerRewardData prd = storage.loadOrCreate(playerUuid);
        int day = prd.getCurrentDay();

        if (prd.isClaimedDay(day)) {
            cmd.set("#ClaimDayLabel.Text", L("gui.already_claimed_today"));
        } else {
            RewardDay rd = calendarService.getRewardForDay(day);
            if (rd != null) {
                String desc = L("gui.claim_description",
                        "day", String.valueOf(day),
                        "coins", MessageUtil.formatCoins(rd.getCoins()),
                        "xp", String.valueOf(rd.getXp()));
                if (!rd.getItems().isEmpty()) {
                    desc += " + " + formatItemsShort(rd.getItems());
                }
                cmd.set("#ClaimDayLabel.Text", desc);
            } else {
                cmd.set("#ClaimDayLabel.Text", L("gui.cannot_claim_yet"));
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  RE-OPEN + STATIC OPEN
    // ════════════════════════════════════════════════════════

    private void reopen(@Nullable String error, @Nullable String success) {
        close();
        RewardsCalendarGui newPage = new RewardsCalendarGui(
                calendarService, streakService, returnService, rewardService,
                antiAbuse, storage, lang, config, playerRef, playerUuid,
                error, success);
        PageOpenHelper.openPage(savedRef, savedStore, newPage);
    }

    public static void open(@Nonnull CalendarService calendarService,
                            @Nonnull StreakService streakService,
                            @Nonnull ReturnRewardService returnService,
                            @Nonnull RewardService rewardService,
                            @Nonnull AntiAbuseGuard antiAbuse,
                            @Nonnull RewardStorage storage,
                            @Nonnull LangManager lang,
                            @Nonnull RewardsConfig config,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull UUID playerUuid) {
        RewardsCalendarGui page = new RewardsCalendarGui(
                calendarService, streakService, returnService, rewardService,
                antiAbuse, storage, lang, config, playerRef, playerUuid);
        PageOpenHelper.openPage(ref, store, page);
    }

    // ════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════

    private String L(String key, String... args) {
        return lang.getForPlayer(playerUuid, key, args);
    }

    private String statusText(@Nonnull DayStatus status) {
        return L("day.status." + status.getId());
    }

    /**
     * Returns a dynamic RGBA background color for a calendar day cell based on status.
     * Milestone days (7,14,21,28,30) get enhanced accents.
     */
    private static String dayBackgroundColor(int day, @Nonnull DayStatus status) {
        boolean milestone = (day % 7 == 0) || day == 30;
        return switch (status) {
            case CLAIMED   -> milestone ? "#122a18d9" : "#0f2214cc"; // darker green tint
            case AVAILABLE -> milestone ? "#2a2808e6" : "#1a2808e0"; // gold/green glow
            case MISSED    -> "#1a0a0ab0";                           // red dim
            case LOCKED    -> milestone ? "#14141abb" : "#0c0e16aa"; // subtle dark
        };
    }

    /**
     * Returns a hex color string for the status label based on DayStatus.
     */
    private static String statusColor(@Nonnull DayStatus status) {
        return switch (status) {
            case CLAIMED   -> "#44dd66";
            case AVAILABLE -> "#f0c040";
            case MISSED    -> "#dd4444";
            case LOCKED    -> "#667788";
        };
    }

    private static String formatSingleItem(@Nonnull String itemEntry) {
        String[] parts = itemEntry.split(":");
        String rawName;
        String count = "1";
        if (parts.length >= 3) {
            rawName = parts[1];
            count = parts[2];
        } else if (parts.length == 2) {
            try {
                Integer.parseInt(parts[1]);
                rawName = parts[0];
                count = parts[1];
            } catch (NumberFormatException e) {
                rawName = parts[1];
            }
        } else {
            rawName = parts[0];
        }
        String name = capitalize(rawName.replace('_', ' '));
        if (name.length() > 14) name = name.substring(0, 12) + "..";
        if (!"1".equals(count)) name += " x" + count;
        return name;
    }

    private static String formatItemsShort(@Nonnull java.util.List<String> items) {
        if (items.isEmpty()) return "";
        int limit = Math.min(items.size(), 2);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatSingleItem(items.get(i)));
        }
        if (items.size() > 2) sb.append(" +").append(items.size() - 2);
        return sb.toString();
    }

    /**
     * Extracts the item identifier from a reward item string.
     * Formats: "namespace:item_name:count", "item_name:count", "item_name"
     * Returns the full ID in format "hytale:item_name" suitable for ItemSlot.
     */
    @Nullable
    private static String extractItemId(@Nonnull String itemEntry) {
        if (itemEntry == null || itemEntry.isEmpty()) return null;
        String[] parts = itemEntry.split(":");
        if (parts.length >= 3) {
            return parts[0] + ":" + parts[1]; // namespace:item_name
        } else if (parts.length == 2) {
            try {
                Integer.parseInt(parts[1]);
                return "hytale:" + parts[0]; // item_name:count → hytale:item_name
            } catch (NumberFormatException e) {
                return parts[0] + ":" + parts[1]; // namespace:item_name
            }
        } else {
            return "hytale:" + parts[0]; // just item_name
        }
    }

    /**
     * Extracts item name suitable for ItemIcon.ItemId (e.g. "Weapon_Sword_Crude")
     * from reward item string formats: "item:count", "ns:item:count", "item".
     */
    @Nullable
    private static String extractItemNameForIcon(@Nonnull String itemEntry) {
        if (itemEntry == null || itemEntry.isEmpty()) return null;
        String[] parts = itemEntry.split(":");
        if (parts.length >= 3) {
            return parts[1]; // namespace:item_name:count → item_name
        } else if (parts.length == 2) {
            try {
                Integer.parseInt(parts[1]);
                return parts[0]; // item_name:count → item_name
            } catch (NumberFormatException e) {
                return parts[1]; // namespace:item_name → item_name
            }
        }
        return parts[0]; // just item_name
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String stripForUI(String text) {
        if (text == null) return "";
        return text.replace("\u2714 ", "").replace("\u2714", "")
                   .replaceAll("<[^>]+>", "").trim();
    }

    private static int parseSlot(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    // ════════════════════════════════════════════════════════
    //  EVENT DATA CLASS
    // ════════════════════════════════════════════════════════

    public static class CalendarEventData {
        public String action = "";
        public String slot = "";
    }
}
