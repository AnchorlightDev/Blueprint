package dev.anchorlight.blueprint.service;

import dev.anchorlight.blueprint.BlueprintPlugin;
import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.database.BlueprintStorage;
import dev.anchorlight.blueprint.database.StorageException;
import dev.anchorlight.blueprint.model.AuditAction;
import dev.anchorlight.blueprint.model.SnapshotMetadata;
import dev.anchorlight.blueprint.model.WorldMetadata;
import dev.anchorlight.blueprint.model.WorldStatus;
import dev.anchorlight.blueprint.util.FileUtil;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Manages snapshot creation, listing, restoration, and deletion.
 *
 * <p>Snapshot folder layout:</p>
 * <pre>plugins/Blueprint/snapshots/&lt;worldName&gt;/&lt;snapshotId&gt;/</pre>
 */
public class SnapshotService {

    private static final DateTimeFormatter SNAP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss").withZone(ZoneOffset.UTC);

    private final BlueprintPlugin plugin;
    private final BlueprintStorage storage;
    private final BlueprintConfig config;
    private final WorldService worldService;
    private final OperationLockService opLocks;
    private final Path snapshotRoot;
    private final Logger logger;

    public SnapshotService(
            @NotNull BlueprintPlugin plugin,
            @NotNull BlueprintStorage storage,
            @NotNull BlueprintConfig config,
            @NotNull WorldService worldService,
            @NotNull OperationLockService opLocks) {
        this.plugin       = plugin;
        this.storage      = storage;
        this.config       = config;
        this.worldService = worldService;
        this.opLocks      = opLocks;
        this.logger       = plugin.getLogger();
        this.snapshotRoot = plugin.getDataFolder().toPath().toAbsolutePath().normalize().resolve("snapshots");
    }

    /**
     * Creates a snapshot of the named world asynchronously.
     *
     * @return a future completing with the new {@link SnapshotMetadata}
     */
    public CompletableFuture<SnapshotMetadata> createSnapshot(
            @NotNull String worldName,
            @Nullable UUID actor) {

        CompletableFuture<SnapshotMetadata> future = new CompletableFuture<>();

        if (!config.isSnapshotsEnabled()) {
            future.completeExceptionally(new IllegalStateException("Snapshots are disabled."));
            return future;
        }

        try {
            WorldMetadata meta = worldService.requireWorld(worldName);

            if (!opLocks.tryLock(worldName)) {
                future.completeExceptionally(new IllegalStateException("World '" + worldName + "' is busy."));
                return future;
            }

            String snapshotId = "snap-" + SNAP_FMT.format(Instant.now());
            boolean wasOpen   = meta.getStatus() == WorldStatus.OPEN || meta.getStatus() == WorldStatus.LOCKED;

            // Unload world on main thread before copying
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    if (wasOpen) worldService.unloadBukkitWorld(meta);

                    plugin.getIoExecutor().submit(() -> {
                        try {
                            doCreateSnapshot(meta, snapshotId, actor, wasOpen, future);
                        } catch (Exception e) {
                            opLocks.unlock(worldName);
                            future.completeExceptionally(e);
                        }
                    });
                } catch (Exception e) {
                    opLocks.unlock(worldName);
                    future.completeExceptionally(e);
                }
            });
        } catch (StorageException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    private void doCreateSnapshot(
            @NotNull WorldMetadata meta,
            @NotNull String snapshotId,
            @Nullable UUID actor,
            boolean wasOpen,
            @NotNull CompletableFuture<SnapshotMetadata> future) throws IOException, StorageException {

        Path worldPath      = new File(meta.getFolderName()).toPath().toAbsolutePath().normalize();
        Path snapDir        = snapshotRoot.resolve(meta.getName()).resolve(snapshotId).normalize();

        File root = new File(".").getAbsoluteFile().getParentFile();
        if (root != null) {
            FileUtil.ensureInsideDirectory(root.toPath(), worldPath);
        }
        FileUtil.ensureInsideDirectory(plugin.getDataFolder().toPath(), snapDir);

        logger.info("[Blueprint] Creating snapshot '" + snapshotId + "' for world '" + meta.getName() + "'...");
        FileUtil.copyDirectory(worldPath, snapDir);

        // Prune oldest snapshots if limit exceeded
        pruneOldSnapshots(meta.getName());

        SnapshotMetadata snapMeta = new SnapshotMetadata(snapshotId, meta.getName(), actor, Instant.now(), null);
        storage.saveSnapshot(snapMeta);
        storage.logAudit(AuditAction.CREATE_SNAPSHOT, meta.getName(), actor, snapshotId);
        logger.info("[Blueprint] Snapshot created: " + snapshotId);

        // Restore world load state on main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (wasOpen) {
                    worldService.loadBukkitWorld(meta);
                    meta.setStatus(WorldStatus.OPEN);
                    storage.saveWorld(meta);
                }
                opLocks.unlock(meta.getName());
                future.complete(snapMeta);
            } catch (Exception e) {
                opLocks.unlock(meta.getName());
                future.completeExceptionally(e);
            }
        });
    }

    /**
     * Lists all snapshots for a world, newest first.
     */
    public List<SnapshotMetadata> listSnapshots(@NotNull String worldName) throws StorageException {
        worldService.requireWorld(worldName); // validate world exists
        return storage.listSnapshots(worldName);
    }

    /**
     * Restores a snapshot, replacing the current world folder.
     * An automatic pre-restore backup is taken if configured.
     *
     * @return a future completing when restoration is done
     */
    public CompletableFuture<Void> restoreSnapshot(
            @NotNull String worldName,
            @NotNull String snapshotId,
            @Nullable UUID actor) {

        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            WorldMetadata meta    = worldService.requireWorld(worldName);
            SnapshotMetadata snap = storage.getSnapshot(worldName, snapshotId);
            if (snap == null) {
                future.completeExceptionally(new IllegalArgumentException("Snapshot not found: " + snapshotId));
                return future;
            }

            if (!opLocks.tryLock(worldName)) {
                future.completeExceptionally(new IllegalStateException("World '" + worldName + "' is busy."));
                return future;
            }

            boolean wasOpen = meta.getStatus() == WorldStatus.OPEN || meta.getStatus() == WorldStatus.LOCKED;

            // Unload on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    if (wasOpen) worldService.unloadBukkitWorld(meta);

                    plugin.getIoExecutor().submit(() -> {
                        try {
                            doRestore(meta, snap, actor, wasOpen, future);
                        } catch (Exception e) {
                            opLocks.unlock(worldName);
                            future.completeExceptionally(e);
                        }
                    });
                } catch (Exception e) {
                    opLocks.unlock(worldName);
                    future.completeExceptionally(e);
                }
            });
        } catch (StorageException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    private void doRestore(
            @NotNull WorldMetadata meta,
            @NotNull SnapshotMetadata snap,
            @Nullable UUID actor,
            boolean wasOpen,
            @NotNull CompletableFuture<Void> future) throws IOException, StorageException {

        Path worldPath      = new File(meta.getFolderName()).toPath().toAbsolutePath().normalize();
        Path snapDir        = snapshotRoot.resolve(meta.getName()).resolve(snap.getSnapshotId()).normalize();

        File root = new File(".").getAbsoluteFile().getParentFile();
        if (root != null) {
            FileUtil.ensureInsideDirectory(root.toPath(), worldPath);
        }
        FileUtil.ensureInsideDirectory(plugin.getDataFolder().toPath(), snapDir);

        // Auto-backup before restoring
        if (config.isAutoBackupBeforeRestore()) {
            String backupId = "pre-restore-" + SNAP_FMT.format(Instant.now());
            Path backupDir  = snapshotRoot.resolve(meta.getName()).resolve(backupId);
            logger.info("[Blueprint] Auto-backup before restore: " + backupId);
            FileUtil.copyDirectory(worldPath, backupDir);

            SnapshotMetadata backupMeta = new SnapshotMetadata(
                    backupId, meta.getName(), actor, Instant.now(), "auto-backup before restore of " + snap.getSnapshotId());
            storage.saveSnapshot(backupMeta);
        }

        // Replace world folder
        logger.info("[Blueprint] Restoring snapshot '" + snap.getSnapshotId() + "' into '" + meta.getName() + "'...");
        FileUtil.deleteDirectory(worldPath);
        FileUtil.copyDirectory(snapDir, worldPath);

        // Remove world-unique files so Bukkit assigns a fresh UID
        FileUtil.deleteIfExists(worldPath.resolve("uid.dat"));
        FileUtil.deleteIfExists(worldPath.resolve("session.lock"));

        storage.logAudit(AuditAction.RESTORE_SNAPSHOT, meta.getName(), actor, snap.getSnapshotId());
        logger.info("[Blueprint] Restore complete.");

        // Reload world on main thread if it was open
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (wasOpen) {
                    worldService.loadBukkitWorld(meta);
                    meta.setStatus(WorldStatus.OPEN);
                    meta.markOpened();
                    storage.saveWorld(meta);
                }
                opLocks.unlock(meta.getName());
                future.complete(null);
            } catch (Exception e) {
                opLocks.unlock(meta.getName());
                future.completeExceptionally(e);
            }
        });
    }

    /**
     * Deletes a snapshot folder and its metadata.
     *
     * @return a future completing when deletion is done
     */
    public CompletableFuture<Void> deleteSnapshot(
            @NotNull String worldName,
            @NotNull String snapshotId,
            @Nullable UUID actor) {

        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            worldService.requireWorld(worldName);
            SnapshotMetadata snap = storage.getSnapshot(worldName, snapshotId);
            if (snap == null) {
                future.completeExceptionally(new IllegalArgumentException("Snapshot not found: " + snapshotId));
                return future;
            }

            plugin.getIoExecutor().submit(() -> {
                try {
                    Path snapDir = snapshotRoot.resolve(worldName).resolve(snapshotId).normalize();
                    FileUtil.ensureInsideDirectory(plugin.getDataFolder().toPath(), snapDir);
                    FileUtil.deleteDirectory(snapDir);

                    storage.deleteSnapshot(worldName, snapshotId);
                    storage.logAudit(AuditAction.DELETE_SNAPSHOT, worldName, actor, snapshotId);
                    logger.info("[Blueprint] Deleted snapshot '" + snapshotId + "' for world '" + worldName + "'");
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (StorageException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    /** Removes oldest snapshots if the per-world limit is exceeded. */
    private void pruneOldSnapshots(@NotNull String worldName) throws StorageException {
        int max = config.getMaxSnapshotsPerWorld();
        if (max <= 0) return;

        List<SnapshotMetadata> snapshots = storage.listSnapshots(worldName);
        // listSnapshots returns newest-first; remove from the end
        while (snapshots.size() >= max) {
            SnapshotMetadata oldest = snapshots.get(snapshots.size() - 1);
            try {
                Path dir = snapshotRoot.resolve(worldName).resolve(oldest.getSnapshotId()).normalize();
                FileUtil.deleteDirectory(dir);
            } catch (IOException e) {
                logger.warning("[Blueprint] Failed to prune snapshot folder: " + e.getMessage());
            }
            storage.deleteSnapshot(worldName, oldest.getSnapshotId());
            snapshots.remove(snapshots.size() - 1);
        }
    }
}
