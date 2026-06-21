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

public class LockCommand implements SubCommand {

    private final WorldService worldService;
    private final BlueprintConfig config;
    private final Logger logger;

    public LockCommand(@NotNull WorldService worldService, @NotNull BlueprintConfig config, @NotNull Logger logger) {
        this.worldService = worldService;
        this.config       = config;
        this.logger       = logger;
    }

    @Override public @NotNull String getName() { return "lock"; }
    @Override public @NotNull String getUsage() { return "lock <world>"; }
    @Override public @NotNull String getDescription() { return "Lock a world against modifications"; }
    @Override public @NotNull String getPermission() { return "blueprint.command.lock"; }

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
            worldService.lockWorld(worldName);
            Messages.send(sender, config, "<red>World <yellow>'" + worldName + "'<red> is now <bold>locked</bold>.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            Messages.send(sender, config, "<red>" + e.getMessage());
        } catch (StorageException e) {
            logger.severe("[Blueprint] DB error locking world: " + e.getMessage());
            Messages.send(sender, config, Messages.ERROR_DB_FAILURE);
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            try { return worldService.worldNames(WorldStatus.OPEN, WorldStatus.CLOSED); }
            catch (StorageException e) { return List.of(); }
        }
        return List.of();
    }
}
