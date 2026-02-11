package com.crystalrealm.ecotalerewards.commands;

import com.crystalrealm.ecotalerewards.calendar.CalendarService;
import com.crystalrealm.ecotalerewards.config.ConfigManager;
import com.crystalrealm.ecotalerewards.config.RewardsConfig;
import com.crystalrealm.ecotalerewards.gui.AdminRewardsGui;
import com.crystalrealm.ecotalerewards.gui.RewardsCalendarGui;
import com.crystalrealm.ecotalerewards.lang.LangManager;
import com.crystalrealm.ecotalerewards.model.*;
import com.crystalrealm.ecotalerewards.protection.AntiAbuseGuard;
import com.crystalrealm.ecotalerewards.returns.ReturnRewardService;
import com.crystalrealm.ecotalerewards.rewards.RewardService;
import com.crystalrealm.ecotalerewards.storage.RewardStorage;
import com.crystalrealm.ecotalerewards.streaks.StreakService;
import com.crystalrealm.ecotalerewards.util.MessageUtil;
import com.crystalrealm.ecotalerewards.util.MiniMessageParser;
import com.crystalrealm.ecotalerewards.util.PluginLogger;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Top-level command collection for <code>/rewards</code>.
 *
 * <p>Subcommands:</p>
 * <ul>
 *   <li><b>/rewards</b> — open calendar GUI (alias)</li>
 *   <li><b>/rewards calendar</b> — open calendar GUI</li>
 *   <li><b>/rewards claim</b> — claim daily reward (CLI)</li>
 *   <li><b>/rewards info</b> — show player progress</li>
 *   <li><b>/rewards admin</b> — open admin GUI</li>
 *   <li><b>/rewards reset</b> — admin: reset a player (arg: uuid)</li>
 *   <li><b>/rewards reload</b> — admin: reload config</li>
 *   <li><b>/rewards langen</b> — switch language to English</li>
 *   <li><b>/rewards langru</b> — switch language to Russian</li>
 *   <li><b>/rewards help</b> — show help</li>
 * </ul>
 *
 * @version 1.0.0
 */
public class RewardsCommandCollection extends AbstractCommandCollection {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private static final Set<String> COMMAND_KEYWORDS = Set.of(
            "rewards", "calendar", "claim", "info", "admin",
            "reset", "reload", "langen", "langru", "help"
    );

    // ── Dependencies ────────────────────────────────────────
    private final ConfigManager      configManager;
    private final LangManager        langManager;
    private final CalendarService    calendarService;
    private final StreakService       streakService;
    private final ReturnRewardService returnService;
    private final RewardService      rewardService;
    private final AntiAbuseGuard     antiAbuse;
    private final RewardStorage      storage;
    private final String             pluginVersion;

    public RewardsCommandCollection(@Nonnull ConfigManager configManager,
                                    @Nonnull LangManager langManager,
                                    @Nonnull CalendarService calendarService,
                                    @Nonnull StreakService streakService,
                                    @Nonnull ReturnRewardService returnService,
                                    @Nonnull RewardService rewardService,
                                    @Nonnull AntiAbuseGuard antiAbuse,
                                    @Nonnull RewardStorage storage,
                                    @Nonnull String pluginVersion) {
        super("rewards", "EcoTaleRewards — daily login calendar & streak system");

        this.configManager  = configManager;
        this.langManager    = langManager;
        this.calendarService = calendarService;
        this.streakService  = streakService;
        this.returnService  = returnService;
        this.rewardService  = rewardService;
        this.antiAbuse      = antiAbuse;
        this.storage        = storage;
        this.pluginVersion  = pluginVersion;

        // Register subcommands
        addSubCommand(new CalendarSubCommand());
        addSubCommand(new ClaimSubCommand());
        addSubCommand(new InfoSubCommand());
        addSubCommand(new AdminSubCommand());
        addSubCommand(new ResetSubCommand());
        addSubCommand(new ReloadSubCommand());
        addSubCommand(new LangEnSubCommand());
        addSubCommand(new LangRuSubCommand());
        addSubCommand(new HelpSubCommand());
    }

    // ═════════════════════════════════════════════════════════
    //  /rewards calendar — open calendar GUI
    // ═════════════════════════════════════════════════════════

    private class CalendarSubCommand extends AbstractAsyncCommand {
        CalendarSubCommand() { super("calendar", "Open daily rewards calendar"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(sender, context, "ecotalerewards.use")) return done();

            openGuiForSender(context, sender, false);
            return done();
        }
    }

    // ═════════════════════════════════════════════════════════
    //  /rewards claim — claim daily reward (CLI)
    // ═════════════════════════════════════════════════════════

    private class ClaimSubCommand extends AbstractAsyncCommand {
        ClaimSubCommand() { super("claim", "Claim today's daily reward"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(sender, context, "ecotalerewards.use")) return done();

            UUID uuid = sender.getUuid();
            PlayerRewardData prd = storage.loadOrCreate(uuid);
            LocalDate today = LocalDate.now();
            RewardsConfig config = configManager.getConfig();

            // Anti-abuse
            String abuseReason = antiAbuse.checkCanClaim(prd, today);
            if (abuseReason != null) {
                String msg = switch (abuseReason) {
                    case "min_online"  -> L(sender, "error.min_online",
                            "minutes", String.valueOf(config.getAntiAbuse().getMinOnlineMinutes()));
                    case "cooldown"    -> L(sender, "error.cooldown");
                    case "max_claims"  -> L(sender, "error.max_claims");
                    default            -> L(sender, "error.generic");
                };
                context.sendMessage(msg(msg));
                return done();
            }

            // Calendar check
            if (!calendarService.canClaim(prd, today)) {
                context.sendMessage(msg(L(sender, "error.already_claimed")));
                return done();
            }

            int day = prd.getCurrentDay();
            RewardDay rewardDay = calendarService.getRewardForDay(day);
            if (rewardDay == null) {
                context.sendMessage(msg(L(sender, "error.no_reward")));
                return done();
            }

            // Streak
            streakService.incrementStreak(prd);
            double streakMult = streakService.calculateMultiplier(prd.getStreak());
            double vipMult = rewardService.getVipMultiplier(uuid, sender);

            // Issue reward
            rewardService.issueDayReward(uuid, rewardDay, vipMult, streakMult);
            calendarService.markClaimed(prd, day, today);
            antiAbuse.recordClaim(uuid);

            // Milestone
            StreakMilestone milestone = streakService.checkMilestone(prd.getStreak());
            if (milestone != null) {
                rewardService.issueMilestoneReward(uuid, milestone, vipMult);
            }

            storage.savePlayer(uuid);

            // Output
            String coinStr = MessageUtil.formatCoins(rewardDay.getCoins());
            String msg = L(sender, "success.claimed",
                    "day", String.valueOf(day),
                    "coins", coinStr,
                    "xp", String.valueOf(rewardDay.getXp()));
            context.sendMessage(msg(msg));

            if (milestone != null) {
                context.sendMessage(msg(L(sender, "success.milestone",
                        "days", String.valueOf(milestone.getDays()),
                        "bonus", MessageUtil.formatCoins(milestone.getBonusCoins()))));
            }

            LOGGER.info("[rewards claim] {} claimed day {} (streak={}, mult={})",
                    uuid, day, prd.getStreak(), String.format("%.2f", streakMult));
            return done();
        }
    }

    // ═════════════════════════════════════════════════════════
    //  /rewards info — show player progress
    // ═════════════════════════════════════════════════════════

    private class InfoSubCommand extends AbstractAsyncCommand {
        InfoSubCommand() { super("info", "Show your reward progress"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(sender, context, "ecotalerewards.use")) return done();

            UUID uuid = sender.getUuid();
            PlayerRewardData prd = storage.loadOrCreate(uuid);
            double mult = streakService.calculateMultiplier(prd.getStreak());

            context.sendMessage(msg(L(sender, "cmd.info.header")));
            context.sendMessage(msg(L(sender, "cmd.info.day",
                    "current", String.valueOf(prd.getCurrentDay()),
                    "total", String.valueOf(calendarService.getTotalDays()))));
            context.sendMessage(msg(L(sender, "cmd.info.streak",
                    "streak", String.valueOf(prd.getStreak()),
                    "longest", String.valueOf(prd.getLongestStreak()))));
            context.sendMessage(msg(L(sender, "cmd.info.multiplier",
                    "mult", String.format("%.2f", mult))));
            context.sendMessage(msg(L(sender, "cmd.info.claimed",
                    "count", String.valueOf(prd.getTotalClaimed()))));

            StreakMilestone next = streakService.getNextMilestone(prd.getStreak());
            if (next != null) {
                int remaining = next.getDays() - prd.getStreak();
                context.sendMessage(msg(L(sender, "cmd.info.next_milestone",
                        "days", String.valueOf(next.getDays()),
                        "remaining", String.valueOf(remaining))));
            }

            if (prd.isPendingReturnReward()) {
                ReturnRewardTier tier = returnService.getPlayerReturnTier(prd);
                if (tier != null) {
                    context.sendMessage(msg(L(sender, "cmd.info.return_pending",
                            "coins", MessageUtil.formatCoins(tier.getCoins()),
                            "xp", String.valueOf(tier.getXp()))));
                }
            }

            context.sendMessage(msg(L(sender, "cmd.info.footer")));
            return done();
        }
    }

    // ═════════════════════════════════════════════════════════
    //  /rewards admin — open admin GUI
    // ═════════════════════════════════════════════════════════

    private class AdminSubCommand extends AbstractAsyncCommand {
        AdminSubCommand() { super("admin", "Open admin settings GUI"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(sender, context, "ecotalerewards.admin")) return done();

            LOGGER.info("[rewards admin] sender={}", sender.getDisplayName());
            openGuiForSender(context, sender, true);
            return done();
        }
    }

    // ═════════════════════════════════════════════════════════
    //  /rewards reset <uuid> — admin: reset player data
    // ═════════════════════════════════════════════════════════

    private class ResetSubCommand extends AbstractAsyncCommand {
        ResetSubCommand() { super("reset", "Reset player reward data"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(sender, context, "ecotalerewards.admin")) return done();

            String arg = parseTrailingArg(context);
            if (arg == null || arg.isEmpty()) {
                context.sendMessage(msg(L(sender, "cmd.reset.usage")));
                return done();
            }

            try {
                UUID targetUuid = UUID.fromString(arg);
                storage.deletePlayer(targetUuid);
                context.sendMessage(msg(L(sender, "cmd.reset.success",
                        "uuid", targetUuid.toString())));
                LOGGER.info("[rewards reset] {} reset player {}", sender.getDisplayName(), targetUuid);
            } catch (IllegalArgumentException e) {
                context.sendMessage(msg(L(sender, "cmd.reset.invalid_uuid")));
            }

            return done();
        }
    }

    // ═════════════════════════════════════════════════════════
    //  /rewards reload — admin: reload config
    // ═════════════════════════════════════════════════════════

    private class ReloadSubCommand extends AbstractAsyncCommand {
        ReloadSubCommand() { super("reload", "Reload configuration"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(sender, context, "ecotalerewards.admin")) return done();

            boolean success = configManager.reload();
            if (success) {
                String newLang = configManager.getConfig().getGeneral().getLanguage();
                langManager.reload(newLang);
                calendarService.reload();
                streakService.reload();
                returnService.reload();
                context.sendMessage(msg(L(sender, "cmd.reload.success")));
            } else {
                context.sendMessage(msg(L(sender, "cmd.reload.fail")));
            }
            return done();
        }
    }

    // ═════════════════════════════════════════════════════════
    //  /rewards langen & /rewards langru
    // ═════════════════════════════════════════════════════════

    private class LangEnSubCommand extends AbstractAsyncCommand {
        LangEnSubCommand() { super("langen", "Switch to English"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            langManager.setPlayerLang(sender.getUuid(), "en");
            context.sendMessage(msg(L(sender, "cmd.lang.switched")));
            return done();
        }
    }

    private class LangRuSubCommand extends AbstractAsyncCommand {
        LangRuSubCommand() { super("langru", "Переключить на русский"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            langManager.setPlayerLang(sender.getUuid(), "ru");
            context.sendMessage(msg(L(sender, "cmd.lang.switched")));
            return done();
        }
    }

    // ═════════════════════════════════════════════════════════
    //  /rewards help
    // ═════════════════════════════════════════════════════════

    private class HelpSubCommand extends AbstractAsyncCommand {
        HelpSubCommand() { super("help", "Show help"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();

            context.sendMessage(msg(L(sender, "cmd.help.header")));
            context.sendMessage(msg(L(sender, "cmd.help.rewards")));
            context.sendMessage(msg(L(sender, "cmd.help.calendar")));
            context.sendMessage(msg(L(sender, "cmd.help.claim")));
            context.sendMessage(msg(L(sender, "cmd.help.info")));
            context.sendMessage(msg(L(sender, "cmd.help.admin_reset")));
            context.sendMessage(msg(L(sender, "cmd.help.admin_grant")));
            context.sendMessage(msg(L(sender, "cmd.help.lang")));
            context.sendMessage(msg(L(sender, "cmd.help.help")));
            context.sendMessage(msg(L(sender, "cmd.help.footer")));
            return done();
        }
    }

    // ═════════════════════════════════════════════════════════
    //  GUI OPENER (reflection, world-thread dispatch)
    // ═════════════════════════════════════════════════════════

    /**
     * Opens custom UI (calendar or admin panel) for the given sender.
     * Resolves {@link PlayerRef} on the world thread via reflection.
     */
    private void openGuiForSender(CommandContext context, CommandSender sender, boolean admin) {
        if (sender instanceof Player player) {
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                try {
                    java.lang.reflect.Method getExt = store.getClass()
                            .getMethod("getExternalData");
                    Object extData = getExt.invoke(store);
                    java.lang.reflect.Method getWorld = extData.getClass()
                            .getMethod("getWorld");
                    Object worldObj = getWorld.invoke(extData);

                    if (worldObj instanceof java.util.concurrent.Executor worldExec) {
                        CompletableFuture.runAsync(() -> {
                            try {
                                java.lang.reflect.Method getComp = store.getClass()
                                        .getMethod("getComponent", Ref.class, ComponentType.class);
                                Object result = getComp.invoke(store, ref,
                                        PlayerRef.getComponentType());
                                if (result instanceof PlayerRef playerRef) {
                                    UUID uuid = sender.getUuid();
                                    RewardsConfig cfg = configManager.getConfig();
                                    if (admin) {
                                        AdminRewardsGui.open(configManager, langManager,
                                                storage, playerRef, ref, store,
                                                uuid, pluginVersion);
                                    } else {
                                        RewardsCalendarGui.open(calendarService, streakService,
                                                returnService, rewardService, antiAbuse,
                                                storage, langManager, cfg,
                                                playerRef, ref, store, uuid);
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.error("[rewards {}] failed on WorldThread",
                                        admin ? "admin" : "gui", e);
                            }
                        }, worldExec);
                    } else {
                        LOGGER.warn("[rewards {}] World is not an Executor",
                                admin ? "admin" : "gui");
                    }
                } catch (ReflectiveOperationException e) {
                    LOGGER.error("[rewards {}] reflection failed",
                            admin ? "admin" : "gui", e);
                    context.sendMessage(msg("<red>Failed to open GUI.</red>"));
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════
    //  HELPERS
    // ═════════════════════════════════════════════════════════

    /**
     * Parses trailing argument from {@code getInputString()},
     * filtering out known command keywords.
     */
    private String parseTrailingArg(CommandContext context) {
        try {
            String input = context.getInputString();
            if (input == null || input.isBlank()) return null;

            String[] parts = input.trim().split("\\s+");
            List<String> args = new ArrayList<>();
            for (String part : parts) {
                String lower = part.toLowerCase();
                if (lower.startsWith("/")) lower = lower.substring(1);
                if (!COMMAND_KEYWORDS.contains(lower)) {
                    args.add(part);
                }
            }
            return args.isEmpty() ? null : args.get(args.size() - 1);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse trailing arg: {}", e.getMessage());
        }
        return null;
    }

    private String L(CommandSender sender, String key, String... args) {
        return langManager.getForPlayer(sender.getUuid(), key, args);
    }

    private boolean checkPerm(CommandSender sender, CommandContext ctx, String perm) {
        if (!sender.hasPermission(perm)) {
            ctx.sendMessage(msg(L(sender, "cmd.no_permission")));
            return false;
        }
        return true;
    }

    private static com.hypixel.hytale.server.core.Message msg(String miniMessage) {
        return com.hypixel.hytale.server.core.Message.parse(MiniMessageParser.toJson(miniMessage));
    }

    private static CompletableFuture<Void> done() {
        return CompletableFuture.completedFuture(null);
    }
}
