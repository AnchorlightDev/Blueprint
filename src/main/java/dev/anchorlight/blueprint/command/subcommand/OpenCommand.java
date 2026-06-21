package dev.anchorlight.blueprint.command.subcommand;

import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.database.StorageException;
import dev.anchorlight.blueprint.model.WorldStatus;
import dev.anchorlight.blueprint.service.WorldService;
import dev.anchorlight.blueprint.util.Messages;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Logger;

public class OpenCommand implements SubCommand {

    private final WorldService worldService;
    private final BlueprintConfig config;
    private final Logger logger;

    public OpenCommand(@NotNull WorldService worldService, @NotNull BlueprintConfig config, @NotNull Logger logger) {
        this.worldService = worldService;
        this.config       = config;
        this.logger       = logger;
    }

    @Override public @NotNull String getName() { return "open"; }
    @Override public @NotNull String getUsage() { return "open <world>"; }
    @Override public @NotNull String getDescription() { return "Load and open a world"; }
    @Override public @NotNull String getPermission() { return "blueprint.command.open"; }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
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
            worldService.openWorld(worldName);
            Messages.send(sender, config, "<green>World <yellow>'" + worldName + "'<green> is now open.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            Messages.send(sender, config, "<red>" + e.getMessage());
        } catch (StorageException e) {
            logger.severe("[Blueprint] DB error opening world: " + e.getMessage());
            Messages.send(sender, config, Messages.ERROR_DB_FAILURE);
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            try { return worldService.worldNames(WorldStatus.CLOSED); }
            catch (StorageException e) { return List.of(); }
        }
        return List.of();
    }
}
