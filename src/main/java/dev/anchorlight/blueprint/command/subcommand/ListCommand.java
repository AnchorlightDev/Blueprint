package dev.anchorlight.blueprint.command.subcommand;

import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.database.StorageException;
import dev.anchorlight.blueprint.model.WorldMetadata;
import dev.anchorlight.blueprint.service.WorldService;
import dev.anchorlight.blueprint.util.Messages;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Logger;

public class ListCommand implements SubCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final WorldService worldService;
    private final BlueprintConfig config;
    private final Logger logger;

    public ListCommand(@NotNull WorldService worldService, @NotNull BlueprintConfig config, @NotNull Logger logger) {
        this.worldService = worldService;
        this.config       = config;
        this.logger       = logger;
    }

    @Override public @NotNull String getName() { return "list"; }
    @Override public @NotNull String getUsage() { return "list [page]"; }
    @Override public @NotNull String getDescription() { return "List managed build worlds"; }
    @Override public @NotNull String getPermission() { return "blueprint.command.list"; }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            Messages.send(sender, config, Messages.ERROR_NO_PERMISSION);
            return;
        }

        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
                if (page < 1) page = 1;
            } catch (NumberFormatException e) {
                Messages.send(sender, config, "<red>Invalid page number.");
                return;
            }
        }

        try {
            List<WorldMetadata> worlds = worldService.listWorlds(page);
            if (worlds.isEmpty()) {
                Messages.send(sender, config, "<gray>No worlds found" + (page > 1 ? " on page " + page : "") + ".");
                return;
            }

            sender.sendMessage(MM.deserialize(config.getMessagePrefix() + "<aqua>Worlds (page " + page + "):"));
            for (WorldMetadata meta : worlds) {
                String statusColor = switch (meta.getStatus()) {
                    case OPEN     -> "<green>";
                    case CLOSED   -> "<gray>";
                    case LOCKED   -> "<red>";
                    case ARCHIVED -> "<dark_gray>";
                };
                sender.sendMessage(MM.deserialize(
                        "  <yellow>" + meta.getName()
                        + " <dark_gray>| " + statusColor + meta.getStatus().name()
                        + " <dark_gray>| <white>" + meta.getFolderName()));
            }
        } catch (StorageException e) {
            logger.severe("[Blueprint] DB error listing worlds: " + e.getMessage());
            Messages.send(sender, config, Messages.ERROR_DB_FAILURE);
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return List.of();
    }
}
