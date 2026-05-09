package dev.anchorlight.blueprint.command.subcommand;

import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.database.StorageException;
import dev.anchorlight.blueprint.model.SnapshotMetadata;
import dev.anchorlight.blueprint.service.SnapshotService;
import dev.anchorlight.blueprint.service.WorldService;
import dev.anchorlight.blueprint.util.Messages;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Dispatches snapshot sub-commands:
 * snapshot create|list|restore|delete
 */
public class SnapshotCommand implements SubCommand {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SnapshotService snapshotService;
    private final WorldService worldService;
    private final BlueprintConfig config;
    private final Logger logger;

    public SnapshotCommand(@NotNull SnapshotService snapshotService, @NotNull WorldService worldService,
                           @NotNull BlueprintConfig config, @NotNull Logger logger) {
        this.snapshotService = snapshotService;
        this.worldService    = worldService;
        this.config          = config;
        this.logger          = logger;
    }

    @Override public @NotNull String getName() { return "snapshot"; }
    @Override public @NotNull String getUsage() { return "snapshot <create|list|restore|delete> <world> [snapshotId]"; }
    @Override public @NotNull String getDescription() { return "Manage world snapshots"; }
    @Override public @NotNull String getPermission() { return "blueprint.command.snapshot"; }

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

        String action    = args[0].toLowerCase();
        String worldName = args[1].toLowerCase();
        UUID actor       = sender instanceof Player p ? p.getUniqueId() : null;

        switch (action) {
            case "create"  -> doCreate(sender, worldName, actor);
            case "list"    -> doList(sender, worldName);
            case "restore" -> {
                if (args.length < 3) {
                    Messages.send(sender, config, "<red>Usage: <yellow>/blueprint snapshot restore <world> <snapshotId>");
                    return;
                }
                doRestore(sender, worldName, args[2], actor);
            }
            case "delete"  -> {
                if (args.length < 3) {
                    Messages.send(sender, config, "<red>Usage: <yellow>/blueprint snapshot delete <world> <snapshotId>");
                    return;
                }
                doDelete(sender, worldName, args[2], actor);
            }
            default -> Messages.send(sender, config, "<red>Unknown snapshot action. Use create, list, restore, or delete.");
        }
    }

    private void doCreate(@NotNull CommandSender sender, @NotNull String worldName, UUID actor) {
        Messages.send(sender, config, "<gray>Creating snapshot for <yellow>'" + worldName + "'<gray>...");
        snapshotService.createSnapshot(worldName, actor).whenComplete((meta, ex) -> {
            if (ex != null) {
                handleError(sender, ex, "snapshot creation");
            } else {
                Messages.send(sender, config, "<green>Snapshot <yellow>'" + meta.getSnapshotId() + "'<green> created.");
            }
        });
    }

    private void doList(@NotNull CommandSender sender, @NotNull String worldName) {
        try {
            List<SnapshotMetadata> snapshots = snapshotService.listSnapshots(worldName);
            if (snapshots.isEmpty()) {
                Messages.send(sender, config, "<gray>No snapshots for world <yellow>'" + worldName + "'<gray>.");
                return;
            }
            sender.sendMessage(MM.deserialize(config.getMessagePrefix() + "<aqua>Snapshots for '" + worldName + "':"));
            for (SnapshotMetadata snap : snapshots) {
                String label = snap.getLabel() != null ? " <dark_gray>(" + snap.getLabel() + ")" : "";
                sender.sendMessage(MM.deserialize(
                        "  <yellow>" + snap.getSnapshotId()
                        + " <dark_gray>| <white>" + DISPLAY_FMT.format(snap.getCreatedAt()) + " UTC" + label));
            }
        } catch (StorageException e) {
            logger.severe("[Blueprint] DB error listing snapshots: " + e.getMessage());
            Messages.send(sender, config, Messages.ERROR_DB_FAILURE);
        } catch (IllegalArgumentException e) {
            Messages.send(sender, config, "<red>" + e.getMessage());
        }
    }

    private void doRestore(@NotNull CommandSender sender, @NotNull String worldName,
                           @NotNull String snapshotId, UUID actor) {
        Messages.send(sender, config, "<gray>Restoring snapshot <yellow>'" + snapshotId + "'<gray>...");
        snapshotService.restoreSnapshot(worldName, snapshotId, actor).whenComplete((v, ex) -> {
            if (ex != null) {
                handleError(sender, ex, "snapshot restore");
            } else {
                Messages.send(sender, config, "<green>Snapshot restored successfully.");
            }
        });
    }

    private void doDelete(@NotNull CommandSender sender, @NotNull String worldName,
                          @NotNull String snapshotId, UUID actor) {
        Messages.send(sender, config, "<gray>Deleting snapshot <yellow>'" + snapshotId + "'<gray>...");
        snapshotService.deleteSnapshot(worldName, snapshotId, actor).whenComplete((v, ex) -> {
            if (ex != null) {
                handleError(sender, ex, "snapshot delete");
            } else {
                Messages.send(sender, config, "<green>Snapshot <yellow>'" + snapshotId + "'<green> deleted.");
            }
        });
    }

    private void handleError(@NotNull CommandSender sender, @NotNull Throwable ex, @NotNull String context) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof IllegalArgumentException || cause instanceof IllegalStateException) {
            Messages.send(sender, config, "<red>" + cause.getMessage());
        } else {
            logger.severe("[Blueprint] Error during " + context + ": " + cause.getMessage());
            Messages.send(sender, config, Messages.ERROR_IO_FAILURE);
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) return List.of("create", "list", "restore", "delete");
        if (args.length == 2) {
            try { return worldService.worldNames(); }
            catch (StorageException e) { return List.of(); }
        }
        return List.of();
    }
}
