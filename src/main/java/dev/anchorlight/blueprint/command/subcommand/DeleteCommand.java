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

public class DeleteCommand implements SubCommand {

    private final WorldService worldService;
    private final OperationLockService opLocks;
    private final BlueprintConfig config;
    private final Logger logger;

    public DeleteCommand(
            @NotNull WorldService worldService,
            @NotNull OperationLockService opLocks,
            @NotNull BlueprintConfig config,
            @NotNull Logger logger) {
        this.worldService = worldService;
        this.opLocks      = opLocks;
        this.config       = config;
        this.logger       = logger;
    }

    @Override public @NotNull String getName() { return "delete"; }
    @Override public @NotNull String getUsage() { return "delete <world> confirm"; }
    @Override public @NotNull String getDescription() { return "Permanently delete a world"; }
    @Override public @NotNull String getPermission() { return "blueprint.command.delete"; }

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

        // Confirmation check
        if (config.isRequireDeleteConfirmation()) {
            if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                Messages.send(sender, config,
                        "<red>This will <bold>permanently delete</bold> world <yellow>'" + worldName + "'<red>!");
                Messages.send(sender, config,
                        "<red>Run <yellow>/blueprint delete " + worldName + " confirm<red> to confirm.");
                return;
            }
        }

        if (!opLocks.tryLock(worldName)) {
            Messages.send(sender, config, String.format(Messages.ERROR_BUSY, worldName));
            return;
        }

        UUID actor = sender instanceof Player p ? p.getUniqueId() : null;
        Messages.send(sender, config, "<gray>Deleting world <yellow>'" + worldName + "'<gray>...");

        // Run IO on a background thread
        // World unload is handled inside WorldService.deleteWorld() via main-thread scheduling
        new Thread(() -> {
            try {
                worldService.deleteWorld(worldName, actor);
                Messages.send(sender, config, "<green>World <yellow>'" + worldName + "'<green> deleted.");
            } catch (IllegalArgumentException | IllegalStateException e) {
                Messages.send(sender, config, "<red>" + e.getMessage());
            } catch (StorageException e) {
                logger.severe("[Blueprint] DB error deleting world: " + e.getMessage());
                Messages.send(sender, config, Messages.ERROR_DB_FAILURE);
            } catch (IOException e) {
                logger.severe("[Blueprint] IO error deleting world: " + e.getMessage());
                Messages.send(sender, config, Messages.ERROR_IO_FAILURE);
            } finally {
                opLocks.unlock(worldName);
            }
        }, "Blueprint-Delete-" + worldName).start();
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return List.of("confirm");
        return List.of();
    }
}
