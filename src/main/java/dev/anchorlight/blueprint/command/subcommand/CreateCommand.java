package dev.anchorlight.blueprint.command.subcommand;

import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.database.StorageException;
import dev.anchorlight.blueprint.model.WorldMetadata;
import dev.anchorlight.blueprint.service.WorldService;
import dev.anchorlight.blueprint.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class CreateCommand implements SubCommand {

    private final WorldService worldService;
    private final BlueprintConfig config;
    private final Logger logger;

    public CreateCommand(@NotNull WorldService worldService, @NotNull BlueprintConfig config, @NotNull Logger logger) {
        this.worldService = worldService;
        this.config       = config;
        this.logger       = logger;
    }

    @Override public @NotNull String getName() { return "create"; }
    @Override public @NotNull String getUsage() { return "create <world>"; }
    @Override public @NotNull String getDescription() { return "Create a new build world"; }
    @Override public @NotNull String getPermission() { return "blueprint.command.create"; }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            Messages.send(sender, config, Messages.ERROR_NO_PERMISSION);
            return;
        }
        if (args.length != 1) {
            Messages.send(sender, config, "<red>Usage: <yellow>/blueprint " + getUsage());
            return;
        }

        String worldName = args[0].toLowerCase();
        UUID ownerUuid   = sender instanceof Player p ? p.getUniqueId() : null;

        Messages.send(sender, config, "<gray>Creating world <yellow>'" + worldName + "'<gray>...");

        try {
            WorldMetadata meta = worldService.createWorld(worldName, ownerUuid);
            Messages.send(sender, config, "<green>World <yellow>'" + meta.getName() + "'<green> created successfully!");

            // Auto TP if it's a player
            if (sender instanceof Player p) {
                worldService.teleport(p, meta.getName());
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            Messages.send(sender, config, "<red>" + e.getMessage());
        } catch (StorageException e) {
            logger.severe("[Blueprint] Database error during world creation: " + e.getMessage());
            Messages.send(sender, config, Messages.ERROR_DB_FAILURE);
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return List.of();
    }
}
