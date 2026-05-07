package dev.anchorlight.blueprint.util;

import dev.anchorlight.blueprint.config.BlueprintConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Helpers for building and dispatching MiniMessage-formatted messages.
 */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Messages() {}

    /** Parses and sends a prefixed MiniMessage string to a sender. */
    public static void send(@NotNull CommandSender sender, @NotNull BlueprintConfig config, @NotNull String miniMessage) {
        sender.sendMessage(parse(config, miniMessage));
    }

    /** Sends a raw (un-prefixed) MiniMessage string. */
    public static void sendRaw(@NotNull CommandSender sender, @NotNull String miniMessage) {
        sender.sendMessage(MM.deserialize(miniMessage));
    }

    /** Returns a prefixed {@link Component} from a MiniMessage string. */
    public static Component parse(@NotNull BlueprintConfig config, @NotNull String miniMessage) {
        return MM.deserialize(config.getMessagePrefix() + miniMessage);
    }

    /** Returns a {@link Component} from a raw MiniMessage string (no prefix). */
    public static Component parseRaw(@NotNull String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    // ── Common message constants ──────────────────────────────────────────

    public static final String ERROR_NO_PERMISSION    = "<red>You don't have permission to do that.";
    public static final String ERROR_PLAYER_ONLY      = "<red>This command can only be used by players.";
    public static final String ERROR_DB_FAILURE       = "<red>A database error occurred. Check the console for details.";
    public static final String ERROR_IO_FAILURE       = "<red>A file-system error occurred. Check the console for details.";
    public static final String ERROR_WORLD_NOT_FOUND  = "<red>World <yellow>'%s'</yellow><red> not found.";
    public static final String ERROR_WORLD_EXISTS     = "<red>A world named <yellow>'%s'</yellow><red> already exists.";
    public static final String ERROR_INVALID_NAME     = "<red>Invalid world name. Use 3–32 characters: lowercase letters, numbers, hyphens, underscores.";
    public static final String ERROR_BUSY             = "<red>World <yellow>'%s'</yellow><red> is currently busy. Try again later.";
    public static final String ERROR_SNAPSHOT_DISABLED = "<red>Snapshots are disabled in the configuration.";
    public static final String ERROR_SNAPSHOT_NOT_FOUND = "<red>Snapshot <yellow>'%s'</yellow><red> not found.";
}
