package com.crystalrealm.ecotalerewards.gui;

import com.crystalrealm.ecotalerewards.config.ConfigManager;
import com.crystalrealm.ecotalerewards.config.RewardsConfig;
import com.crystalrealm.ecotalerewards.lang.LangManager;
import com.crystalrealm.ecotalerewards.storage.RewardStorage;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin settings GUI for EcoTaleRewards.
 *
 * <p>Settings (S1–S8), Actions (AB1–AB3), Stats, and Calendar Editor.</p>
 *
 * <p>Calendar Editor allows navigating through days 1-30 and adjusting
 * Coins, XP, and Items for each day with +/- buttons.</p>
 *
 * @version 1.1.0
 */
public final class AdminRewardsGui extends InteractiveCustomUIPage<AdminRewardsGui.AdminEventData> {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private static final String PAGE_PATH = "Pages/CrystalRealm_EcoTaleRewards_AdminPanel.ui";
    private static final int MAX_DAYS = 30;

    // ── Predefined item presets for the visual picker grid ─
    private static final int ITEMS_PER_PAGE = 24; // 4 rows × 6 cols
    private static final List<String> ITEM_PRESETS = Arrays.asList(
            // ── Swords ──
            "Weapon_Sword_Crude:1", "Weapon_Sword_Copper:1", "Weapon_Sword_Bronze:1",
            "Weapon_Sword_Iron:1", "Weapon_Sword_Cobalt:1", "Weapon_Sword_Silver:1",
            "Weapon_Sword_Gold:1", "Weapon_Sword_Thorium:1", "Weapon_Sword_Adamantite:1",
            "Weapon_Sword_Mithril:1", "Weapon_Sword_Onyxium:1",
            // ── Staffs ──
            "Weapon_Staff_Wood:1", "Weapon_Staff_Copper:1", "Weapon_Staff_Bronze:1",
            "Weapon_Staff_Iron:1", "Weapon_Staff_Cobalt:1", "Weapon_Staff_Adamantite:1",
            "Weapon_Staff_Mithril:1", "Weapon_Staff_Onyxium:1",
            "Weapon_Staff_Frost:1", "Weapon_Staff_Wizard:1", "Weapon_Staff_Cane:1",
            // ── Shields ──
            "Weapon_Shield_Copper:1", "Weapon_Shield_Bronze:1", "Weapon_Shield_Iron:1",
            "Weapon_Shield_Cobalt:1", "Weapon_Shield_Adamantite:1",
            // ── Pickaxes ──
            "Tool_Pickaxe_Crude:1", "Tool_Pickaxe_Copper:1", "Tool_Pickaxe_Bronze:1",
            "Tool_Pickaxe_Iron:1", "Tool_Pickaxe_Cobalt:1", "Tool_Pickaxe_Adamantite:1",
            "Tool_Pickaxe_Mithril:1", "Tool_Pickaxe_Onyxium:1",
            // ── Hatchets ──
            "Tool_Hatchet_Crude:1", "Tool_Hatchet_Copper:1", "Tool_Hatchet_Bronze:1",
            "Tool_Hatchet_Iron:1", "Tool_Hatchet_Cobalt:1", "Tool_Hatchet_Adamantite:1",
            // ── Potions ──
            "Potion_Health:1", "Potion_Health:3", "Potion_Health:5",
            "Potion_Stamina:1", "Potion_Stamina:3", "Potion_Stamina:5",
            "Potion_Morph:1",
            // ── Crops (food/farming) ──
            "Plant_Crop_Wheat_Item:16", "Plant_Crop_Carrot_Item:16",
            "Plant_Crop_Potato_Item:16", "Plant_Crop_Corn_Item:16",
            "Plant_Crop_Tomato_Item:16", "Plant_Crop_Onion_Item:16",
            "Plant_Crop_Rice_Item:16", "Plant_Crop_Lettuce_Item:16",
            "Plant_Crop_Pumpkin_Item:1", "Plant_Crop_Aubergine_Item:16",
            "Plant_Crop_Cauliflower_Item:16", "Plant_Crop_Chilli_Item:16",
            "Plant_Crop_Turnip_Item:16",
            // ── Furniture ──
            "Furniture_Crude_Torch:8", "Furniture_Crude_Torch:16",
            "Furniture_Crude_Chest_Small:1", "Furniture_Crude_Bed:1",
            "Furniture_Feran_Bed:1",
            // ── Misc ──
            "Bench_Campfire:1", "Bench_Builders:1",
            "Ingredient_Hay:16"
    );

    // ── Event data keys ─────────────────────────────────────
    private static final String KEY_ACTION = "Action";
    private static final String KEY_SLOT   = "Slot";

    static final BuilderCodec<AdminEventData> CODEC = ReflectiveCodecBuilder
            .<AdminEventData>create(AdminEventData.class, AdminEventData::new)
            .addStringField(KEY_ACTION, (d, v) -> d.action = v, d -> d.action)
            .addStringField(KEY_SLOT,   (d, v) -> d.slot = v,   d -> d.slot)
            .build();

    // ── Dependencies ────────────────────────────────────────
    private final ConfigManager  configManager;
    private final LangManager    langManager;
    private final RewardStorage  storage;
    private final UUID           playerUuid;
    private final String         pluginVersion;

    @Nullable private final String errorMessage;
    @Nullable private final String successMessage;

    private Ref<EntityStore>   savedRef;
    private Store<EntityStore> savedStore;

    // ── Calendar editor state ───────────────────────────────
    private int editingDay = 1;
    private int itemPickerPage = 0;

    // ════════════════════════════════════════════════════════
    //  CONSTRUCTORS
    // ════════════════════════════════════════════════════════

    public AdminRewardsGui(@Nonnull ConfigManager configManager,
                           @Nonnull LangManager langManager,
                           @Nonnull RewardStorage storage,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull UUID playerUuid,
                           @Nonnull String pluginVersion) {
        this(configManager, langManager, storage, playerRef, playerUuid, pluginVersion, null, null);
    }

    public AdminRewardsGui(@Nonnull ConfigManager configManager,
                           @Nonnull LangManager langManager,
                           @Nonnull RewardStorage storage,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull UUID playerUuid,
                           @Nonnull String pluginVersion,
                           @Nullable String errorMessage,
                           @Nullable String successMessage) {
        super(playerRef, CustomPageLifetime.CanDismiss, CODEC);
        this.configManager  = configManager;
        this.langManager    = langManager;
        this.storage        = storage;
        this.playerUuid     = playerUuid;
        this.pluginVersion  = pluginVersion;
        this.errorMessage   = errorMessage;
        this.successMessage = successMessage;
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
        cmd.set("#TitleLabel.Text", L("gui.admin.title"));

        // Section labels
        cmd.set("#SecGeneral.Text", L("gui.admin.sec_general"));
        cmd.set("#SecAntiAbuse.Text", L("gui.admin.sec_antiabuse"));
        cmd.set("#SecStreak.Text", L("gui.admin.sec_streak"));
        cmd.set("#SecActions.Text", L("gui.admin.sec_actions"));
        cmd.set("#SecStats.Text", L("gui.admin.sec_stats"));
        cmd.set("#SecCalEditor.Text", L("gui.admin.sec_calendar_editor"));

        // ── Bind events ─────────────────────────────────────

        // Toggle buttons (S1, S2, S3)
        for (int s : new int[]{1, 2, 3}) {
            events.addEventBinding(CustomUIEventBindingType.Activating, "#S" + s + "Toggle",
                    new EventData().append(KEY_ACTION, "toggle").append(KEY_SLOT, String.valueOf(s)));
        }

        // Increment buttons (S4–S8)
        for (int s = 4; s <= 8; s++) {
            events.addEventBinding(CustomUIEventBindingType.Activating, "#S" + s + "Down",
                    new EventData().append(KEY_ACTION, "dec").append(KEY_SLOT, String.valueOf(s)));
            events.addEventBinding(CustomUIEventBindingType.Activating, "#S" + s + "Up",
                    new EventData().append(KEY_ACTION, "inc").append(KEY_SLOT, String.valueOf(s)));
        }

        // Action buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#AB1",
                new EventData().append(KEY_ACTION, "reload"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#AB2",
                new EventData().append(KEY_ACTION, "save"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#AB3",
                new EventData().append(KEY_ACTION, "reset_all"));

        // ── Calendar editor event bindings ───────────────────
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DayPrev",
                new EventData().append(KEY_ACTION, "day_prev"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DayNext",
                new EventData().append(KEY_ACTION, "day_next"));

        // Coins +/-
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CoinsDn50",
                new EventData().append(KEY_ACTION, "coins_adj").append(KEY_SLOT, "-50"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CoinsUp50",
                new EventData().append(KEY_ACTION, "coins_adj").append(KEY_SLOT, "50"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CoinsDn200",
                new EventData().append(KEY_ACTION, "coins_adj").append(KEY_SLOT, "-200"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CoinsUp200",
                new EventData().append(KEY_ACTION, "coins_adj").append(KEY_SLOT, "200"));

        // XP +/-
        events.addEventBinding(CustomUIEventBindingType.Activating, "#XPDn25",
                new EventData().append(KEY_ACTION, "xp_adj").append(KEY_SLOT, "-25"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#XPUp25",
                new EventData().append(KEY_ACTION, "xp_adj").append(KEY_SLOT, "25"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#XPDn100",
                new EventData().append(KEY_ACTION, "xp_adj").append(KEY_SLOT, "-100"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#XPUp100",
                new EventData().append(KEY_ACTION, "xp_adj").append(KEY_SLOT, "100"));

        // Item management: picker grid + remove + pagination
        for (int ip = 0; ip < ITEMS_PER_PAGE; ip++) {
            events.addEventBinding(CustomUIEventBindingType.Activating, "#IP" + ip,
                    new EventData().append(KEY_ACTION, "pick_item").append(KEY_SLOT, String.valueOf(ip)));
        }
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ItemRemoveLast",
                new EventData().append(KEY_ACTION, "item_remove"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PickerPrev",
                new EventData().append(KEY_ACTION, "picker_prev"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PickerNext",
                new EventData().append(KEY_ACTION, "picker_next"));

        // Save day
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveDay",
                new EventData().append(KEY_ACTION, "save_day"));

        // ── Banners ─────────────────────────────────────────
        if (errorMessage != null && !errorMessage.isEmpty()) {
            cmd.set("#ErrorBanner.Visible", true);
            cmd.set("#ErrorText.Text", stripForUI(errorMessage));
        }
        if (successMessage != null && !successMessage.isEmpty()) {
            cmd.set("#SuccessBanner.Visible", true);
            cmd.set("#SuccessText.Text", stripForUI(successMessage));
        }

        // ── Fill settings data ──────────────────────────────
        updateSettingsData(cmd);
        updateCalendarEditor(cmd);

        LOGGER.info("Admin rewards GUI built for {}", playerUuid);
    }

    // ════════════════════════════════════════════════════════
    //  HANDLE EVENTS
    // ════════════════════════════════════════════════════════

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull AdminEventData data) {
        RewardsConfig config = configManager.getConfig();

        switch (data.action) {
            case "toggle" -> {
                int slot = parseSlot(data.slot);
                handleToggle(slot, config);
                saveConfig();
                refreshPage(null, null);
            }

            case "inc" -> {
                int slot = parseSlot(data.slot);
                handleIncrement(slot, config, 1);
                saveConfig();
                refreshPage(null, null);
            }

            case "dec" -> {
                int slot = parseSlot(data.slot);
                handleIncrement(slot, config, -1);
                saveConfig();
                refreshPage(null, null);
            }

            case "reload" -> {
                boolean success = configManager.reload();
                if (success) {
                    String newLang = configManager.getConfig().getGeneral().getLanguage();
                    langManager.reload(newLang);
                    refreshPage(null, L("cmd.reload.success"));
                } else {
                    refreshPage(L("cmd.reload.fail"), null);
                }
            }

            case "save" -> {
                try {
                    storage.saveAll();
                    refreshPage(null, L("gui.admin.data_saved"));
                } catch (Exception e) {
                    refreshPage(L("gui.admin.data_save_fail"), null);
                }
            }

            case "reset_all" -> {
                LOGGER.warn("Admin {} requested full reset via GUI", playerUuid);
                refreshPage(L("gui.admin.reset_warning"), null);
            }

            // ── Calendar editor actions ─────────────────────
            case "day_prev" -> {
                editingDay = Math.max(1, editingDay - 1);
                refreshPage(null, null);
            }

            case "day_next" -> {
                editingDay = Math.min(MAX_DAYS, editingDay + 1);
                refreshPage(null, null);
            }

            case "coins_adj" -> {
                int delta = parseSlot(data.slot);
                adjustDayCoins(delta);
                refreshPage(null, null);
            }

            case "xp_adj" -> {
                int delta = parseSlot(data.slot);
                adjustDayXP(delta);
                refreshPage(null, null);
            }

            case "item_prev" -> {
                // Legacy: no-op (replaced by picker grid)
                refreshPage(null, null);
            }

            case "item_next" -> {
                // Legacy: no-op (replaced by picker grid)
                refreshPage(null, null);
            }

            case "pick_item" -> {
                int slotOnPage = parseSlot(data.slot);
                int presetIdx = itemPickerPage * ITEMS_PER_PAGE + slotOnPage;
                if (presetIdx >= 0 && presetIdx < ITEM_PRESETS.size()) {
                    addItemToDay(ITEM_PRESETS.get(presetIdx));
                }
                refreshPage(null, null);
            }

            case "picker_prev" -> {
                if (itemPickerPage > 0) itemPickerPage--;
                refreshPage(null, null);
            }

            case "picker_next" -> {
                int maxPage = (ITEM_PRESETS.size() - 1) / ITEMS_PER_PAGE;
                if (itemPickerPage < maxPage) itemPickerPage++;
                refreshPage(null, null);
            }

            case "item_add" -> {
                // Legacy: no-op (replaced by pick_item)
                refreshPage(null, null);
            }

            case "item_remove" -> {
                removeLastItemFromDay();
                refreshPage(null, null);
            }

            case "save_day" -> {
                saveConfig();
                refreshPage(null, L("gui.admin.day_saved", "day", String.valueOf(editingDay)));
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  CALENDAR EDITOR LOGIC
    // ════════════════════════════════════════════════════════

    private RewardsConfig.DayRewardEntry getOrCreateDayEntry(int day) {
        RewardsConfig config = configManager.getConfig();
        Map<String, RewardsConfig.DayRewardEntry> days = config.getCalendar().getDays();
        String key = String.valueOf(day);
        RewardsConfig.DayRewardEntry entry = days.get(key);
        if (entry == null) {
            entry = new RewardsConfig.DayRewardEntry();
            days.put(key, entry);
        }
        return entry;
    }

    private void adjustDayCoins(int delta) {
        RewardsConfig.DayRewardEntry entry = getOrCreateDayEntry(editingDay);
        entry.setCoins(Math.max(0, entry.getCoins() + delta));
    }

    private void adjustDayXP(int delta) {
        RewardsConfig.DayRewardEntry entry = getOrCreateDayEntry(editingDay);
        entry.setXP(Math.max(0, entry.getXP() + delta));
    }

    private void addItemToDay(String item) {
        RewardsConfig.DayRewardEntry entry = getOrCreateDayEntry(editingDay);
        List<String> items = new ArrayList<>(entry.getItems());
        items.add(item);
        entry.setItems(items);
    }

    private void removeLastItemFromDay() {
        RewardsConfig.DayRewardEntry entry = getOrCreateDayEntry(editingDay);
        List<String> items = new ArrayList<>(entry.getItems());
        if (!items.isEmpty()) {
            items.remove(items.size() - 1);
            entry.setItems(items);
        }
    }

    // ════════════════════════════════════════════════════════
    //  TOGGLE / INCREMENT HANDLERS
    // ════════════════════════════════════════════════════════

    private void handleToggle(int slot, RewardsConfig config) {
        switch (slot) {
            case 1 -> config.getGeneral().setDebugMode(!config.getGeneral().isDebugMode());
            case 2 -> config.getGeneral().setLanguage(
                    "en".equalsIgnoreCase(config.getGeneral().getLanguage()) ? "ru" : "en");
            case 3 -> config.getCalendar().setStrictMode(!config.getCalendar().isStrictMode());
        }
    }

    private void handleIncrement(int slot, RewardsConfig config, int delta) {
        switch (slot) {
            case 4 -> config.getGeneral().setAutoSaveIntervalMinutes(
                    Math.max(1, config.getGeneral().getAutoSaveIntervalMinutes() + delta));
            case 5 -> config.getAntiAbuse().setMinOnlineMinutes(
                    Math.max(0, config.getAntiAbuse().getMinOnlineMinutes() + delta));
            case 6 -> {
                // RelogCooldownMinutes doesn't have a setter; add one if needed
            }
            case 7 -> {
                // MaxClaimsPerDay doesn't have a setter; skip
            }
            case 8 -> config.getCalendar().setGraceDays(
                    Math.max(0, config.getCalendar().getGraceDays() + delta));
        }
    }

    // ════════════════════════════════════════════════════════
    //  REFRESH PAGE
    // ════════════════════════════════════════════════════════

    private void refreshPage(@Nullable String error, @Nullable String success) {
        try {
            UICommandBuilder cmd = new UICommandBuilder();

            cmd.set("#ErrorBanner.Visible", error != null && !error.isEmpty());
            if (error != null && !error.isEmpty()) cmd.set("#ErrorText.Text", stripForUI(error));
            cmd.set("#SuccessBanner.Visible", success != null && !success.isEmpty());
            if (success != null && !success.isEmpty()) cmd.set("#SuccessText.Text", stripForUI(success));

            updateSettingsData(cmd);
            updateCalendarEditor(cmd);
            sendUpdate(cmd);
        } catch (Exception e) {
            LOGGER.warn("[refreshPage] sendUpdate failed: {}", e.getMessage());
            reopen(error, success);
        }
    }

    // ════════════════════════════════════════════════════════
    //  SETTINGS DATA
    // ════════════════════════════════════════════════════════

    private void updateSettingsData(@Nonnull UICommandBuilder cmd) {
        RewardsConfig config = configManager.getConfig();
        RewardsConfig.GeneralSection    gen   = config.getGeneral();
        RewardsConfig.CalendarSection   cal   = config.getCalendar();
        RewardsConfig.AntiAbuseSection  abuse = config.getAntiAbuse();

        String toggleText = L("gui.admin.toggle");

        // Section labels (re-set on every update so language changes take effect)
        cmd.set("#SecGeneral.Text", L("gui.admin.sec_general"));
        cmd.set("#SecAntiAbuse.Text", L("gui.admin.sec_antiabuse"));
        cmd.set("#SecStreak.Text", L("gui.admin.sec_streak"));
        cmd.set("#SecActions.Text", L("gui.admin.sec_actions"));
        cmd.set("#SecStats.Text", L("gui.admin.sec_stats"));
        cmd.set("#SecCalEditor.Text", L("gui.admin.sec_calendar_editor"));
        cmd.set("#TitleLabel.Text", L("gui.admin.title"));

        // S1: Debug Mode
        cmd.set("#S1Label.Text", L("gui.admin.debug_mode"));
        cmd.set("#S1Value.Text", boolText(gen.isDebugMode()));
        cmd.set("#S1Toggle.Text", toggleText);

        // S2: Language
        cmd.set("#S2Label.Text", L("gui.admin.language"));
        cmd.set("#S2Value.Text", gen.getLanguage().toUpperCase());
        cmd.set("#S2Toggle.Text", toggleText);

        // S3: Calendar Mode
        cmd.set("#S3Label.Text", L("gui.admin.calendar_mode"));
        cmd.set("#S3Value.Text", cal.isStrictMode() ? "STRICT" : "SOFT");
        cmd.set("#S3Toggle.Text", toggleText);

        // S4: Auto-Save Interval
        cmd.set("#S4Label.Text", L("gui.admin.autosave_interval"));
        cmd.set("#S4Value.Text", gen.getAutoSaveIntervalMinutes() + " min");

        // S5: Min Online Minutes
        cmd.set("#S5Label.Text", L("gui.admin.min_online"));
        cmd.set("#S5Value.Text", abuse.getMinOnlineMinutes() + " min");

        // S6: Relog Cooldown Minutes
        cmd.set("#S6Label.Text", L("gui.admin.relog_cooldown"));
        cmd.set("#S6Value.Text", abuse.getRelogCooldownMinutes() + " min");

        // S7: Max Claims Per Day
        cmd.set("#S7Label.Text", L("gui.admin.max_claims"));
        cmd.set("#S7Value.Text", String.valueOf(abuse.getMaxClaimsPerDay()));

        // S8: Grace Days
        cmd.set("#S8Label.Text", L("gui.admin.grace_days"));
        cmd.set("#S8Value.Text", String.valueOf(cal.getGraceDays()));

        // Action button labels
        cmd.set("#AB1.Text", L("gui.admin.btn_reload"));
        cmd.set("#AB2.Text", L("gui.admin.btn_save"));
        cmd.set("#AB3.Text", L("gui.admin.btn_reset"));

        // Stats
        cmd.set("#StatPlayersLabel.Text", L("gui.admin.stat_players"));
        cmd.set("#StatPlayersValue.Text", "—");
        cmd.set("#StatTotalClaimsLabel.Text", L("gui.admin.stat_total_claims"));
        cmd.set("#StatTotalClaimsValue.Text", "—");
        cmd.set("#StatVersionLabel.Text", L("gui.admin.stat_version"));
        cmd.set("#StatVersionValue.Text", "v" + pluginVersion);
    }

    // ════════════════════════════════════════════════════════
    //  CALENDAR EDITOR DATA
    // ════════════════════════════════════════════════════════

    private void updateCalendarEditor(@Nonnull UICommandBuilder cmd) {
        RewardsConfig.DayRewardEntry entry = getOrCreateDayEntry(editingDay);

        // Day selector
        cmd.set("#DayNumLabel.Text", L("gui.admin.day_num", "day", String.valueOf(editingDay)));
        cmd.set("#DayDescLabel.Text", entry.getDescription());

        // Coins
        cmd.set("#EdCoinsLabel.Text", L("gui.admin.ed_coins"));
        cmd.set("#EdCoinsValue.Text", String.format("%.0f", entry.getCoins()));

        // XP
        cmd.set("#EdXPLabel.Text", L("gui.admin.ed_xp"));
        cmd.set("#EdXPValue.Text", String.valueOf(entry.getXP()));

        // Items label + icons for current day items
        cmd.set("#EdItemsLabel.Text", L("gui.admin.ed_items"));
        List<String> items = entry.getItems();
        if (items.isEmpty()) {
            cmd.set("#EdItemsValue.Text", "—");
        } else {
            cmd.set("#EdItemsValue.Text", formatItemList(items));
        }

        // Show icons for current day items (up to 4)
        for (int ei = 0; ei < 4; ei++) {
            if (ei < items.size()) {
                cmd.set("#EdIcon" + ei + ".Visible", true);
                String iconId = extractItemNameForIcon(items.get(ei));
                if (iconId != null) {
                    cmd.set("#EdIcon" + ei + ".ItemId", iconId);
                }
            } else {
                cmd.set("#EdIcon" + ei + ".Visible", false);
            }
        }

        // Picker label and button labels
        cmd.set("#EdPickerLabel.Text", L("gui.admin.pick_item_label"));
        cmd.set("#ItemRemoveLast.Text", L("gui.admin.btn_remove_item"));
        cmd.set("#SaveDay.Text", L("gui.admin.btn_save_day"));

        // Pagination controls
        int totalPages = Math.max(1, (ITEM_PRESETS.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        itemPickerPage = Math.min(itemPickerPage, totalPages - 1);
        int pageStart = itemPickerPage * ITEMS_PER_PAGE;

        cmd.set("#PickerPageLabel.Text", (itemPickerPage + 1) + " / " + totalPages);
        cmd.set("#PickerPrev.Visible", itemPickerPage > 0);
        cmd.set("#PickerNext.Visible", itemPickerPage < totalPages - 1);

        // Populate item picker grid: show items for the current page
        for (int ip = 0; ip < ITEMS_PER_PAGE; ip++) {
            int presetIdx = pageStart + ip;
            String prefix = "#IP" + ip;
            if (presetIdx < ITEM_PRESETS.size()) {
                String preset = ITEM_PRESETS.get(presetIdx);
                String iconId = extractItemNameForIcon(preset);
                String displayName = extractShortDisplayName(preset);
                cmd.set(prefix + ".Visible", true);
                if (iconId != null) {
                    cmd.set(prefix + "Icon.ItemId", iconId);
                }
                cmd.set(prefix + "Name.Text", displayName);
            } else {
                cmd.set(prefix + ".Visible", false);
            }
        }
    }

    /**
     * Extracts item name for ItemIcon.ItemId (e.g. "Weapon_Sword_Crude")
     */
    @Nullable
    private static String extractItemNameForIcon(@Nonnull String itemEntry) {
        if (itemEntry == null || itemEntry.isEmpty()) return null;
        String[] parts = itemEntry.split(":");
        if (parts.length >= 3) {
            return parts[1];
        } else if (parts.length == 2) {
            try {
                Integer.parseInt(parts[1]);
                return parts[0];
            } catch (NumberFormatException e) {
                return parts[1];
            }
        }
        return parts[0];
    }

    /**
     * Short display name for the picker grid (e.g. "Sword Crude x1")
     */
    private static String extractShortDisplayName(@Nonnull String preset) {
        String[] parts = preset.split(":");
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
        // Shorten: remove common prefixes for compact display
        String name = rawName.replace('_', ' ');
        for (String prefix : new String[]{"Weapon ", "Tool ", "Plant Crop ", "Furniture Crude ", "Bench "}) {
            if (name.startsWith(prefix)) {
                name = name.substring(prefix.length());
                break;
            }
        }
        if (!"1".equals(count)) name += " x" + count;
        return name;
    }

    private static String formatItemList(@Nonnull List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            String item = items.get(i);
            String[] parts = item.split(":");
            String name;
            String count = "1";
            if (parts.length >= 3) {
                name = parts[1].replace('_', ' ');
                count = parts[2];
            } else if (parts.length == 2) {
                name = parts[0].replace('_', ' ');
                count = parts[1];
            } else {
                name = parts[0].replace('_', ' ');
            }
            sb.append(name);
            if (!"1".equals(count)) sb.append(" x").append(count);
        }
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════
    //  RE-OPEN + STATIC OPEN
    // ════════════════════════════════════════════════════════

    private void reopen(@Nullable String error, @Nullable String success) {
        close();
        AdminRewardsGui newPage = new AdminRewardsGui(
                configManager, langManager, storage, playerRef,
                playerUuid, pluginVersion, error, success);
        PageOpenHelper.openPage(savedRef, savedStore, newPage);
    }

    public static void open(@Nonnull ConfigManager configManager,
                            @Nonnull LangManager langManager,
                            @Nonnull RewardStorage storage,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull UUID playerUuid,
                            @Nonnull String pluginVersion) {
        AdminRewardsGui page = new AdminRewardsGui(
                configManager, langManager, storage, playerRef,
                playerUuid, pluginVersion);
        PageOpenHelper.openPage(ref, store, page);
    }

    // ════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════

    private String L(String key, String... args) {
        return langManager.getForPlayer(playerUuid, key, args);
    }

    private static String stripForUI(String text) {
        if (text == null) return "";
        return text.replace("\u2714 ", "").replace("\u2714", "")
                   .replaceAll("<[^>]+>", "").trim();
    }

    private static String boolText(boolean val) {
        return val ? "ON" : "OFF";
    }

    private static int parseSlot(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private void saveConfig() {
        try {
            configManager.saveConfig();
        } catch (Exception e) {
            LOGGER.warn("Failed to save config: {}", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════
    //  EVENT DATA CLASS
    // ════════════════════════════════════════════════════════

    public static class AdminEventData {
        public String action = "";
        public String slot = "";
    }
}
