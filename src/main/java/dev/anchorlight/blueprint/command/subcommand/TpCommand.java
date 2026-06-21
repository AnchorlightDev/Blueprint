package dev.anchorlight.blueprint.command.subcommand;

import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.database.StorageException;
import dev.anchorlight.blueprint.model.WorldStatus;
import dev.anchorlight.blueprint.service.WorldService;
import dev.anchorlight.blueprint.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Logger;

public class TpCommand implements SubCommand {

    private final WorldService worldService;
    private final BlueprintConfig config;
    private final Logger logger;

    public TpCommand(@NotNull WorldService worldService, @NotNull BlueprintConfig config, @NotNull Logger logger) {
        this.worldService = worldService;
        this.config       = config;
        this.logger       = logger;
    }

    @Override public @NotNull String getName() { return "tp"; }
    @Override public @NotNull String getUsage() { return "tp <world>"; }
    @Override public @NotNull String getDescription() { return "Teleport to a build world"; }
    @Override public @NotNull String getPermission() { return "blueprint.command.teleport"; }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, config, Messages.ERROR_PLAYER_ONLY);
            return;
        }
        if (!sender.hasPermission(getPermission())) {
            Messages.send(sender, config, Messages.ERROR_NO_PERMISSION);
            return;
        }
        if (args.length < 1) {
            Messages.send(sender, config, "<red>Usage: <yellow>/blueprint " + getUsage());
            return;
        }

        String worldName = args[0].toLowerCase();
        try {
            worldService.teleport(player, worldName);
            Messages.send(sender, config, "<green>Teleported to <yellow>'" + worldName + "'<green>.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            Messages.send(sender, config, "<red>" + e.getMessage());
        } catch (StorageException e) {
            logger.severe("[Blueprint] DB error during teleport: " + e.getMessage());
            Messages.send(sender, config, Messages.ERROR_DB_FAILURE);
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            try { return worldService.worldNames(); } // Show all worlds
            catch (StorageException e) { return List.of(); }
        }
        return List.of();
    }
}
