package dev.anchorlight.blueprint.command.subcommand;

import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.database.StorageException;
import dev.anchorlight.blueprint.service.OperationLockService;
import dev.anchorlight.blueprint.service.WorldService;
import dev.anchorlight.blueprint.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class RenameCommand implements SubCommand {

    private final WorldService worldService;
    private final OperationLockService opLocks;
    private final BlueprintConfig config;
    private final Logger logger;

    public RenameCommand(
            @NotNull WorldService worldService,
            @NotNull OperationLockService opLocks,
            @NotNull BlueprintConfig config,
            @NotNull Logger logger) {
        this.worldService = worldService;
        this.opLocks      = opLocks;
        this.config       = config;
        this.logger       = logger;
    }

    @Override public @NotNull String getName() { return "rename"; }
    @Override public @NotNull String getUsage() { return "rename <world> <new-name>"; }
    @Override public @NotNull String getDescription() { return "Rename a world to a new name"; }
    @Override public @NotNull String getPermission() { return "blueprint.command.rename"; }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            Messages.send(sender, config, Messages.ERROR_NO_PERMISSION);
            return;
        }
        if (args.length < 2) {
            Messages.send(sender, config, "<red>Usage: <yellow>/blueprint " + getUsage());
            return;
        }

        String oldName = args[0].toLowerCase();
        String newName = args[1].toLowerCase();

        if (oldName.equals(newName)) {
            Messages.send(sender, config, "<red>New name must be different from the current name.");
            return;
        }

        if (!opLocks.tryLock(oldName)) {
            Messages.send(sender, config, String.format(Messages.ERROR_BUSY, oldName));
            return;
        }

        UUID actor = sender instanceof Player p ? p.getUniqueId() : null;
        Messages.send(sender, config,
                "<gray>Renaming <yellow>'" + oldName + "'<gray> to <yellow>'" + newName + "'<gray>...");

        new Thread(() -> {
            try {
                worldService.renameWorld(oldName, newName, actor);
                Messages.send(sender, config,
                        "<green>World <yellow>'" + oldName + "'<green> renamed to <yellow>'" + newName + "'<green>.");
            } catch (IllegalArgumentException | IllegalStateException e) {
                Messages.send(sender, config, "<red>" + e.getMessage());
            } catch (StorageException e) {
                logger.severe("[Blueprint] DB error renaming world: " + e.getMessage());
                Messages.send(sender, config, Messages.ERROR_DB_FAILURE);
            } catch (IOException e) {
                logger.severe("[Blueprint] IO error renaming world: " + e.getMessage());
                Messages.send(sender, config, Messages.ERROR_IO_FAILURE);
            } finally {
                opLocks.unlock(oldName);
            }
        }, "Blueprint-Rename-" + oldName).start();
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            try { return worldService.worldNames(); }
            catch (StorageException e) { return List.of(); }
        }
        return List.of();
    }
}
