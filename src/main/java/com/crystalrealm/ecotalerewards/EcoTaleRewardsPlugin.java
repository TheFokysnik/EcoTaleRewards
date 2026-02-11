package com.crystalrealm.ecotalerewards;

import com.crystalrealm.ecotalerewards.calendar.CalendarService;
import com.crystalrealm.ecotalerewards.commands.RewardsCommandCollection;
import com.crystalrealm.ecotalerewards.config.ConfigManager;
import com.crystalrealm.ecotalerewards.config.RewardsConfig;
import com.crystalrealm.ecotalerewards.gui.RewardsCalendarGui;
import com.crystalrealm.ecotalerewards.lang.LangManager;
import com.crystalrealm.ecotalerewards.model.PlayerRewardData;
import com.crystalrealm.ecotalerewards.model.ReturnRewardTier;
import com.crystalrealm.ecotalerewards.model.StreakMilestone;
import com.crystalrealm.ecotalerewards.protection.AntiAbuseGuard;
import com.crystalrealm.ecotalerewards.returns.ReturnRewardService;
import com.crystalrealm.ecotalerewards.rewards.RewardService;
import com.crystalrealm.ecotalerewards.storage.JsonRewardStorage;
import com.crystalrealm.ecotalerewards.storage.RewardStorage;
import com.crystalrealm.ecotalerewards.streaks.StreakService;
import com.crystalrealm.ecotalerewards.util.MessageUtil;
import com.crystalrealm.ecotalerewards.util.PluginLogger;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * EcoTaleRewards — MMORPG retention system for Hytale servers.
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>30-day daily login calendar with escalating rewards</li>
 *   <li>Streak system with milestones &amp; multiplier</li>
 *   <li>Return-to-game rewards for absent players</li>
 *   <li>Anti-abuse protection (min online time, relog cooldown, daily limit)</li>
 *   <li>Native GUI — calendar panel &amp; admin panel</li>
 *   <li>VIP multipliers via permission tiers</li>
 *   <li>JSON storage, full RU/EN localization</li>
 * </ul>
 *
 * @version 1.0.0
 */
public class EcoTaleRewardsPlugin extends JavaPlugin {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();
    private static final String VERSION = "1.0.0";

    // ── Services ────────────────────────────────────────────
    private ConfigManager      configManager;
    private LangManager        langManager;
    private RewardStorage      storage;
    private CalendarService    calendarService;
    private StreakService       streakService;
    private ReturnRewardService returnService;
    private RewardService      rewardService;
    private AntiAbuseGuard     antiAbuse;

    // ── Scheduled tasks ─────────────────────────────────────
    private ScheduledFuture<?> autoSaveTask;

    public EcoTaleRewardsPlugin(JavaPluginInit init) {
        super(init);
    }

    // ═════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═════════════════════════════════════════════════════════

    @Override
    protected void setup() {
        LOGGER.info("═══════════════════════════════════════");
        LOGGER.info("  EcoTaleRewards v{} — setup", VERSION);
        LOGGER.info("═══════════════════════════════════════");

        // 1. Config
        configManager = new ConfigManager(getDataDirectory());
        configManager.loadOrCreate();
        RewardsConfig config = configManager.getConfig();

        // 2. Language
        langManager = new LangManager(getDataDirectory());
        langManager.load(config.getGeneral().getLanguage());

        // 3. Storage
        storage = new JsonRewardStorage(getDataDirectory());
        storage.initialize();

        // 4. Calendar service
        calendarService = new CalendarService(config);

        // 5. Streak service
        streakService = new StreakService(config);

        // 6. Return reward service
        returnService = new ReturnRewardService(config);

        // 7. Reward issuer
        rewardService = new RewardService(config, this);

        // 8. Anti-abuse
        antiAbuse = new AntiAbuseGuard(config);

        // 9. Commands
        getCommandRegistry().registerCommand(new RewardsCommandCollection(
                configManager, langManager, calendarService, streakService,
                returnService, rewardService, antiAbuse, storage, VERSION
        ));
        LOGGER.info("Registered /rewards command.");

        // 10. Player join event listener
        registerPlayerJoinHandler();
    }

    @Override
    protected void start() {
        LOGGER.info("EcoTaleRewards starting...");

        // Schedule auto-save
        int saveInterval = configManager.getConfig().getGeneral().getAutoSaveIntervalMinutes();
        autoSaveTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> {
                    try {
                        storage.saveAll();
                    } catch (Exception e) {
                        LOGGER.error("Auto-save failed: {}", e.getMessage());
                    }
                },
                saveInterval, saveInterval, TimeUnit.MINUTES
        );

        LOGGER.info("═══════════════════════════════════════");
        LOGGER.info("  EcoTaleRewards v{} — STARTED", VERSION);
        LOGGER.info("  Calendar:  {} days", configManager.getConfig().getCalendar().getTotalDays());
        LOGGER.info("  Streak:    {}", streakService.isEnabled() ? "ENABLED" : "DISABLED");
        LOGGER.info("  Returns:   {}", returnService.isEnabled() ? "ENABLED" : "DISABLED");
        LOGGER.info("  Auto-save: every {} min", saveInterval);
        LOGGER.info("═══════════════════════════════════════");
    }

    @Override
    protected void shutdown() {
        LOGGER.info("EcoTaleRewards shutting down...");

        // Cancel scheduled tasks
        if (autoSaveTask != null) autoSaveTask.cancel(false);

        // Save all player data
        if (storage != null) storage.shutdown();

        // Cleanup
        if (antiAbuse != null) antiAbuse.cleanup();
        MessageUtil.clearCache();
        if (langManager != null) langManager.clearPlayerData();

        LOGGER.info("EcoTaleRewards v{} — shutdown complete.", VERSION);
    }

    // ═════════════════════════════════════════════════════════
    //  PLAYER JOIN HANDLER
    // ═════════════════════════════════════════════════════════

    /**
     * Registers a handler that processes login events:
     * <ol>
     *   <li>Set session join time (for anti-abuse min-online check)</li>
     *   <li>Process calendar: advance day, handle missed days</li>
     *   <li>Handle streak: increment or break</li>
     *   <li>Process return rewards if absent long enough</li>
     *   <li>Send welcome/notification messages</li>
     * </ol>
     *
     * <p>Uses {@link HytaleServer#SCHEDULED_EXECUTOR} with a small delay
     * to ensure the player entity is fully loaded before sending messages.</p>
     */
    private void registerPlayerJoinHandler() {
        // Register using Hytale's native event system.
        // PlayerReadyEvent fires when the player entity is fully loaded.
        // Try register() first (present in all stubs), fallback to registerGlobal().
        try {
            getEventRegistry().register(PlayerReadyEvent.class, this::handlePlayerReady);
            LOGGER.info("Player join listener registered via EventRegistry.register().");
        } catch (Exception | NoSuchMethodError | NoClassDefFoundError e) {
            LOGGER.warn("EventRegistry.register() failed: {} — trying registerGlobal fallback...", e.getMessage());
            try {
                getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::handlePlayerReady);
                LOGGER.info("Player join listener registered via registerGlobal fallback.");
            } catch (Exception | NoSuchMethodError | NoClassDefFoundError e2) {
                LOGGER.warn("Both register methods failed: {} — onPlayerJoin() must be called manually.", e2.getMessage());
            }
        }
    }

    /**
     * Handles {@link PlayerReadyEvent} — the player is fully loaded and ready.
     */
    private void handlePlayerReady(@Nonnull PlayerReadyEvent event) {
        try {
            Player player = event.getPlayer();
            if (player == null) return;
            UUID playerUuid = player.getUuid();
            LOGGER.debug("[PlayerReady] Processing login for {}", playerUuid);

            // Process login directly — player is already ready, no delay needed
            processPlayerLogin(playerUuid);

            // Send notifications
            PlayerRewardData data = storage.loadOrCreate(playerUuid);
            sendLoginNotifications(playerUuid, data, 0, player);
        } catch (Exception e) {
            LOGGER.error("Failed to process PlayerReadyEvent: {}", e.getMessage());
        }
    }

    /**
     * Public entry point for player join processing (for external plugins).
     *
     * @param playerUuid the joining player's UUID
     */
    public void onPlayerJoin(@Nonnull UUID playerUuid) {
        try {
            processPlayerLogin(playerUuid);
        } catch (Exception e) {
            LOGGER.error("Failed to process login for {}: {}", playerUuid, e.getMessage());
        }
    }

    private void processPlayerLogin(@Nonnull UUID playerUuid) {
        PlayerRewardData data = storage.loadOrCreate(playerUuid);
        LocalDate today = LocalDate.now();

        // Set session join time (anti-abuse)
        data.setSessionJoinTime(System.currentTimeMillis());

        // Process calendar (returns absence days if any)
        int absenceDays = calendarService.processLogin(data, today);

        // Handle streak
        if (absenceDays == 0) {
            // Same day or consecutive — do nothing extra
        } else if (absenceDays <= configManager.getConfig().getCalendar().getGraceDays()) {
            // Within grace → streak continues
        } else {
            // Broke streak
            streakService.handleStreakBreak(data, absenceDays);
        }

        // Process return rewards
        if (absenceDays > 0) {
            returnService.processAbsence(data, absenceDays);
        }

        // Save
        storage.savePlayer(playerUuid);

        // Notifications are sent from handlePlayerReady after processPlayerLogin
    }

    private void sendLoginNotifications(@Nonnull UUID playerUuid,
                                        @Nonnull PlayerRewardData data,
                                        int absenceDays,
                                        @Nonnull Player player) {
        RewardsConfig config = configManager.getConfig();

        // Welcome back message
        String welcomeMsg = langManager.getForPlayer(playerUuid, "notify.welcome",
                "day", String.valueOf(data.getCurrentDay()),
                "streak", String.valueOf(data.getStreak()));
        MessageUtil.sendMessage(playerUuid,
                config.getGeneral().getMessagePrefix() + " " + welcomeMsg);

        // Return reward notification
        if (data.isPendingReturnReward()) {
            ReturnRewardTier tier = returnService.getPlayerReturnTier(data);
            if (tier != null) {
                String returnMsg = langManager.getForPlayer(playerUuid, "notify.return_reward",
                        "days", String.valueOf(absenceDays),
                        "coins", MessageUtil.formatCoins(tier.getCoins()),
                        "xp", String.valueOf(tier.getXp()));
                MessageUtil.sendMessage(playerUuid,
                        config.getGeneral().getMessagePrefix() + " " + returnMsg);
            }
        }

        // Unclaimed day reminder
        if (calendarService.canClaim(data, LocalDate.now())) {
            String claimMsg = langManager.getForPlayer(playerUuid, "notify.claim_available",
                    "day", String.valueOf(data.getCurrentDay()));
            MessageUtil.sendMessage(playerUuid,
                    config.getGeneral().getMessagePrefix() + " " + claimMsg);
        }

        // Milestone approaching
        StreakMilestone next = streakService.getNextMilestone(data.getStreak());
        if (next != null) {
            int remaining = next.getDays() - data.getStreak();
            if (remaining <= 3 && remaining > 0) {
                String msMsg = langManager.getForPlayer(playerUuid, "notify.milestone_close",
                        "remaining", String.valueOf(remaining),
                        "days", String.valueOf(next.getDays()));
                MessageUtil.sendMessage(playerUuid,
                        config.getGeneral().getMessagePrefix() + " " + msMsg);
            }
        }

        // Auto-open calendar GUI after a short delay (3s so notifications are seen first)
        HytaleServer.SCHEDULED_EXECUTOR.schedule(
                () -> openCalendarGuiForPlayer(player, playerUuid),
                3, TimeUnit.SECONDS);
    }

    /**
     * Opens the calendar GUI for a player.
     * Uses direct Player reference and World.execute() — same pattern as JoinScreen plugin.
     */
    private void openCalendarGuiForPlayer(@Nonnull Player player, @Nonnull UUID playerUuid) {
        try {
            World world = player.getWorld();
            if (world == null) {
                LOGGER.debug("[autoGUI] Player {} world is null", playerUuid);
                return;
            }

            // Dispatch on the world thread (same pattern as JoinScreen)
            world.execute(() -> {
                try {
                    // Get PlayerRef directly from Player (same as JoinScreen)
                    PlayerRef playerRef = player.getPlayerRef();
                    if (playerRef == null) {
                        LOGGER.debug("[autoGUI] PlayerRef is null for {}", playerUuid);
                        return;
                    }

                    Ref<EntityStore> ref = player.getReference();
                    if (ref == null || !ref.isValid()) {
                        LOGGER.debug("[autoGUI] Invalid ref for player {}", playerUuid);
                        return;
                    }

                    Store<EntityStore> store = ref.getStore();

                    RewardsConfig cfg = configManager.getConfig();
                    RewardsCalendarGui.open(calendarService, streakService,
                            returnService, rewardService, antiAbuse,
                            storage, langManager, cfg,
                            playerRef, ref, store, playerUuid);
                    LOGGER.info("[autoGUI] Calendar GUI opened for {}", playerUuid);
                } catch (Throwable e) {
                    LOGGER.warn("[autoGUI] Failed to open GUI on world thread: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            LOGGER.debug("[autoGUI] Could not auto-open GUI for {}: {}", playerUuid, e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════
    //  GETTERS
    // ═════════════════════════════════════════════════════════

    @Nonnull public ConfigManager       getConfigManager()  { return configManager; }
    @Nonnull public LangManager         getLangManager()     { return langManager; }
    @Nonnull public RewardStorage       getStorage()         { return storage; }
    @Nonnull public CalendarService     getCalendarService() { return calendarService; }
    @Nonnull public StreakService        getStreakService()   { return streakService; }
    @Nonnull public ReturnRewardService getReturnService()   { return returnService; }
    @Nonnull public RewardService       getRewardService()   { return rewardService; }
    @Nonnull public AntiAbuseGuard      getAntiAbuse()       { return antiAbuse; }
    @Nonnull public String              getVersion()         { return VERSION; }
}
