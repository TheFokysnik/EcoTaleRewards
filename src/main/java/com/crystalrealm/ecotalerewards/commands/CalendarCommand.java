package com.crystalrealm.ecotalerewards.commands;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * Standalone <code>/calendar</code> command — opens the daily rewards calendar GUI.
 *
 * <p>This is an alias for <code>/rewards calendar</code>, registered as a separate
 * top-level command for better mod compatibility and ease of use.</p>
 *
 * @version 1.0.0
 */
public class CalendarCommand extends AbstractAsyncCommand {

    private final RewardsCommandCollection delegate;

    public CalendarCommand(@Nonnull RewardsCommandCollection delegate) {
        super("calendar", "Open daily rewards calendar");
        this.setPermissionGroups("Adventure");
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<Void> executeAsync(CommandContext context) {
        if (!context.isPlayer()) return CompletableFuture.completedFuture(null);
        CommandSender sender = context.sender();

        String input = context.getInputString();
        if (input != null && input.trim().toLowerCase().contains("admin")) {
            delegate.openAdminForSender(context, sender);
        } else {
            delegate.openCalendarForSender(context, sender);
        }
        return CompletableFuture.completedFuture(null);
    }
}
