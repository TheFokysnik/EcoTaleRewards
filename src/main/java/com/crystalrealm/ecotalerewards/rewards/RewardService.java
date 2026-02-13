package com.crystalrealm.ecotalerewards.rewards;

import com.crystalrealm.ecotalerewards.config.RewardsConfig;
import com.crystalrealm.ecotalerewards.model.PlayerRewardData;
import com.crystalrealm.ecotalerewards.model.ReturnRewardTier;
import com.crystalrealm.ecotalerewards.model.RewardDay;
import com.crystalrealm.ecotalerewards.model.StreakMilestone;
import com.crystalrealm.ecotalerewards.util.PluginLogger;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Central reward distribution service.
 * Handles coin deposits via EcotaleAPI (reflection), XP via RPG Leveling,
 * item drops via give command, and command execution via CommandManager.
 */
public class RewardService {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final RewardsConfig config;
    private final JavaPlugin plugin;

    // Cached command infrastructure (lazy-init, pure reflection)
    private Object commandManagerObj;
    private Object consoleSenderObj;
    private Method handleCommandMethod;
    private boolean commandSystemReady = false;
    private boolean commandSystemFailed = false;

    // ECS context — set by GUI before reward claims
    private Ref<EntityStore> currentRef;
    private Store<EntityStore> currentStore;

    public RewardService(@Nonnull RewardsConfig config, @Nonnull JavaPlugin plugin) {
        this.config = config;
        this.plugin = plugin;
    }

    /**
     * Register the ECS context (Ref + Store) for the current player.
     * Must be called from the GUI before issuing rewards — this is how we get
     * access to the Player entity for inventory operations.
     */
    public void registerPlayerContext(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        this.currentRef = ref;
        this.currentStore = store;
        LOGGER.info("[RewardService] Player context registered");
    }

    /**
     * Issue a daily calendar reward to the player.
     *
     * @param vipMultiplier VIP tier multiplier (1.0 = no VIP)
     * @param streakMultiplier streak-based multiplier
     * @return true if successfully issued
     */
    public boolean issueDayReward(@Nonnull UUID playerUuid,
                                  @Nonnull RewardDay day,
                                  double vipMultiplier,
                                  double streakMultiplier) {
        BigDecimal totalMult = BigDecimal.valueOf(vipMultiplier)
                .multiply(BigDecimal.valueOf(streakMultiplier));

        BigDecimal coins = day.getCoins()
                .multiply(totalMult)
                .setScale(2, RoundingMode.HALF_UP);

        int xp = (int) Math.round(day.getXp() * totalMult.doubleValue());

        boolean coinsOk = depositCoins(playerUuid, coins, "DailyReward Day " + day.getDay());
        boolean xpOk = grantXP(playerUuid, xp);
        giveItems(playerUuid, day.getItems());
        executeCommands(playerUuid, day.getCommands());

        if (config.getAntiAbuse().isLogAllRewards()) {
            LOGGER.info("[REWARD LOG] Player={} Day={} Coins={} XP={} VIP={} Streak={}",
                    playerUuid, day.getDay(), coins, xp, vipMultiplier, streakMultiplier);
        }

        return coinsOk;
    }

    /**
     * Issue a streak milestone bonus.
     */
    public void issueMilestoneReward(@Nonnull UUID playerUuid,
                                     @Nonnull StreakMilestone milestone,
                                     double vipMultiplier) {
        BigDecimal coins = milestone.getBonusCoins()
                .multiply(BigDecimal.valueOf(vipMultiplier))
                .setScale(2, RoundingMode.HALF_UP);
        int xp = (int) Math.round(milestone.getBonusXP() * vipMultiplier);

        depositCoins(playerUuid, coins, "StreakMilestone " + milestone.getDays() + " days");
        grantXP(playerUuid, xp);
        executeCommands(playerUuid, milestone.getCommands());

        if (config.getAntiAbuse().isLogAllRewards()) {
            LOGGER.info("[REWARD LOG] Player={} StreakMilestone={}d Coins={} XP={}",
                    playerUuid, milestone.getDays(), coins, xp);
        }
    }

    /**
     * Issue a return reward.
     */
    public void issueReturnReward(@Nonnull UUID playerUuid,
                                  @Nonnull ReturnRewardTier tier,
                                  double vipMultiplier) {
        BigDecimal coins = tier.getCoins()
                .multiply(BigDecimal.valueOf(vipMultiplier))
                .setScale(2, RoundingMode.HALF_UP);
        int xp = (int) Math.round(tier.getXp() * vipMultiplier);

        depositCoins(playerUuid, coins, "ReturnReward " + tier.getMinAbsenceDays() + "+ days");
        grantXP(playerUuid, xp);
        executeCommands(playerUuid, tier.getCommands());

        if (config.getAntiAbuse().isLogAllRewards()) {
            LOGGER.info("[REWARD LOG] Player={} ReturnReward tier={}+ Coins={} XP={}",
                    playerUuid, tier.getMinAbsenceDays(), coins, xp);
        }
    }

    /**
     * Get VIP multiplier for a player based on permissions.
     */
    public double getVipMultiplier(@Nonnull UUID playerUuid,
                                   @Nullable Object commandSender) {
        if (commandSender == null) return 1.0;

        List<RewardsConfig.VipTier> vipTiers = config.getVipTiers();
        if (vipTiers == null || vipTiers.isEmpty()) return 1.0;

        try {
            if (commandSender instanceof CommandSender cs) {
                for (RewardsConfig.VipTier tier : vipTiers) {
                    if (cs.hasPermission(tier.getPermission())) return tier.getMultiplier();
                }
            } else {
                Method hasPerm = commandSender.getClass().getMethod("hasPermission", String.class);
                for (RewardsConfig.VipTier tier : vipTiers) {
                    boolean has = (boolean) hasPerm.invoke(commandSender, tier.getPermission());
                    if (has) return tier.getMultiplier();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("VIP permission check failed: {}", e.getMessage());
        }

        return 1.0;
    }

    // ═════════════════════════════════════════════════════════
    //  ECONOMY (EcotaleAPI via reflection)
    // ═════════════════════════════════════════════════════════

    private boolean depositCoins(@Nonnull UUID playerUuid, @Nonnull BigDecimal amount, @Nonnull String reason) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return true;

        try {
            Class<?> apiClass = Class.forName("com.ecotale.api.EcotaleAPI");
            Method deposit = apiClass.getMethod("deposit", UUID.class, double.class, String.class);
            boolean result = (boolean) deposit.invoke(null, playerUuid, amount.doubleValue(), reason);
            if (!result) {
                LOGGER.warn("Deposit failed for {}: {} ({})", playerUuid, amount, reason);
            }
            return result;
        } catch (ClassNotFoundException e) {
            LOGGER.warn("EcotaleAPI not available — coin reward skipped.");
            return false;
        } catch (Exception e) {
            LOGGER.error("Failed to deposit {} to {}: {}", amount, playerUuid, e.getMessage());
            return false;
        }
    }

    private boolean grantXP(@Nonnull UUID playerUuid, int xp) {
        if (xp <= 0) return true;

        try {
            Class<?> apiClass = Class.forName("org.zuxaw.plugin.api.RPGLevelingAPI");
            Method getApi = apiClass.getMethod("get");
            Object api = getApi.invoke(null);
            if (api != null) {
                Method addXP = api.getClass().getMethod("addXP", UUID.class, double.class);
                addXP.invoke(api, playerUuid, (double) xp);
                LOGGER.info("[XP] Granted {} XP to {}", xp, playerUuid);
                return true;
            } else {
                LOGGER.warn("RPGLevelingAPI.get() returned null — XP not granted");
            }
        } catch (ClassNotFoundException ignored) {
            // RPG Leveling not available — XP reward skipped
        } catch (Exception e) {
            LOGGER.warn("XP grant failed for {}: {}", playerUuid, e.getMessage(), e);
        }
        return false;
    }

    private void executeCommands(@Nonnull UUID playerUuid, @Nonnull List<String> commands) {
        if (commands.isEmpty()) return;

        if (!ensureCommandSystem()) {
            LOGGER.warn("Command system not available — {} command(s) skipped for {}", commands.size(), playerUuid);
            return;
        }

        String playerName = resolvePlayerName(playerUuid);
        for (String cmd : commands) {
            String resolved = cmd.replace("{player}", playerName);
            // Strip leading / if present
            resolved = resolved.trim();
            if (resolved.startsWith("/")) {
                resolved = resolved.substring(1).trim();
            }
            if (resolved.isBlank()) continue;

            try {
                handleCommandMethod.invoke(commandManagerObj, consoleSenderObj, resolved);
                LOGGER.info("[CMD] Executed: {}", resolved);
            } catch (Exception e) {
                LOGGER.warn("Command execution failed '{}': {}", resolved, e.getMessage());
            }
        }
    }

    /**
     * Give items to a player directly through Inventory API.
     * Item format: "item_name:count" or "namespace:item_name:count".
     */
    private void giveItems(@Nonnull UUID playerUuid, @Nonnull List<String> items) {
        if (items.isEmpty()) return;

        // Get the Player entity from ECS store
        Player player = getPlayerEntity();
        if (player == null) {
            LOGGER.warn("[ITEM] Player entity not available — {} item(s) skipped for {}", items.size(), playerUuid);
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            LOGGER.warn("[ITEM] Player inventory is null — {} item(s) skipped for {}", items.size(), playerUuid);
            return;
        }

        ItemContainer container = inventory.getCombinedHotbarFirst();
        if (container == null) {
            LOGGER.warn("[ITEM] CombinedHotbarFirst is null — {} item(s) skipped for {}", items.size(), playerUuid);
            return;
        }

        for (String itemEntry : items) {
            String[] parts = itemEntry.split(":");
            String itemId;
            int count = 1;

            if (parts.length >= 2) {
                // Last part might be count
                String lastPart = parts[parts.length - 1];
                try {
                    count = Integer.parseInt(lastPart);
                    // Everything before the last part is the item ID
                    itemId = itemEntry.substring(0, itemEntry.lastIndexOf(":" + lastPart));
                } catch (NumberFormatException e) {
                    // No count suffix — entire string is item ID
                    itemId = itemEntry;
                }
            } else {
                itemId = parts[0];
            }

            try {
                ItemStack stack = new ItemStack(itemId, count);
                ItemStackTransaction tx = container.addItemStack(stack);
                if (tx != null && tx.succeeded()) {
                    LOGGER.info("[ITEM] Gave {} x{} to {}", itemId, count, playerUuid);
                } else {
                    LOGGER.warn("[ITEM] Failed to add {} x{} to inventory of {}", itemId, count, playerUuid);
                }
            } catch (Exception e) {
                LOGGER.warn("[ITEM] Error giving {} x{} to {}: {}", itemId, count, playerUuid, e.getMessage(), e);
            }
        }
    }

    /**
     * Resolve player name from UUID using the Player entity.
     */
    @Nonnull
    private String resolvePlayerName(@Nonnull UUID playerUuid) {
        Player player = getPlayerEntity();
        if (player != null) {
            try {
                Method getUsername = player.getClass().getMethod("getUsername");
                Object name = getUsername.invoke(player);
                if (name instanceof String s && !s.isEmpty()) {
                    return s;
                }
            } catch (Exception ignored) {}
        }
        return playerUuid.toString();
    }

    /**
     * Get the Player entity from the ECS store using the registered Ref + Store context.
     * This is the same pattern BetterBattlePass uses:
     *   Player player = (Player) store.getComponent(ref, Player.getComponentType());
     */
    @Nullable
    private Player getPlayerEntity() {
        if (currentRef == null || currentStore == null) {
            LOGGER.warn("[ECS] No player context registered — call registerPlayerContext first");
            return null;
        }
        try {
            ComponentType<EntityStore, Player> playerType = Player.getComponentType();
            Player player = (Player) currentStore.getComponent(currentRef, playerType);
            if (player == null) {
                LOGGER.warn("[ECS] Player component is null for current ref");
            }
            return player;
        } catch (Exception e) {
            LOGGER.warn("[ECS] Failed to get Player entity: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Lazily initializes the command system.
     * Uses ConsoleSender.INSTANCE as the sender for handleCommand (discovered from BBP).
     */
    private boolean ensureCommandSystem() {
        if (commandSystemReady) return true;
        if (commandSystemFailed) return false;

        // Step 1: Get CommandManager singleton
        Object cmdMgr = null;
        try {
            Class<?> cmdMgrClass = Class.forName("com.hypixel.hytale.server.core.command.system.CommandManager");
            Method getMethod = cmdMgrClass.getMethod("get");
            cmdMgr = getMethod.invoke(null);
            LOGGER.info("[CmdInit] CommandManager.get() → {}", cmdMgr != null ? cmdMgr.getClass().getName() : "null");
        } catch (Exception e) {
            LOGGER.error("[CmdInit] CommandManager.get() failed: {}", e.getMessage());
        }

        if (cmdMgr == null) {
            LOGGER.error("[CmdInit] CommandManager not available.");
            commandSystemFailed = true;
            return false;
        }

        // Step 2: Find handleCommand(CommandSender, String) method
        Method handleCmd = null;
        for (Method m : cmdMgr.getClass().getMethods()) {
            if (m.getName().equals("handleCommand") && m.getParameterCount() == 2
                    && m.getParameterTypes()[1] == String.class) {
                handleCmd = m;
                break;
            }
        }
        if (handleCmd == null) {
            LOGGER.error("[CmdInit] handleCommand not found on CommandManager");
            commandSystemFailed = true;
            return false;
        }

        LOGGER.info("[CmdInit] handleCommand params: ({}, {})",
                handleCmd.getParameterTypes()[0].getName(),
                handleCmd.getParameterTypes()[1].getName());

        // Step 3: Get ConsoleSender.INSTANCE — static field singleton
        Object console = null;
        try {
            Class<?> csClass = Class.forName("com.hypixel.hytale.server.core.console.ConsoleSender");
            Field instanceField = csClass.getField("INSTANCE");
            console = instanceField.get(null);
            LOGGER.info("[CmdInit] ConsoleSender.INSTANCE → {}", console != null ? console.getClass().getName() : "null");
        } catch (Exception e) {
            LOGGER.error("[CmdInit] ConsoleSender.INSTANCE not found: {}", e.getMessage());
        }

        if (console == null) {
            LOGGER.error("[CmdInit] ConsoleSender not available — commands will not work");
            commandSystemFailed = true;
            return false;
        }

        commandManagerObj = cmdMgr;
        consoleSenderObj = console;
        handleCommandMethod = handleCmd;
        commandSystemReady = true;
        LOGGER.info("[CmdInit] Command system ready! Manager={}, Sender={}", 
                cmdMgr.getClass().getName(), console.getClass().getName());
        return true;
    }

    /** Find a declared field by name in the class hierarchy. */
    private Field findField(Class<?> clazz, String name) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
