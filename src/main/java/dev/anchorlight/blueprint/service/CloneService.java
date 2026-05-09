package dev.anchorlight.blueprint.service;

import dev.anchorlight.blueprint.BlueprintPlugin;
import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.database.BlueprintStorage;
import dev.anchorlight.blueprint.database.StorageException;
import dev.anchorlight.blueprint.model.AuditAction;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Handles asynchronous world cloning.
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>Validate source exists, target does not.</li>
 *   <li>Close source world if open (main thread).</li>
 *   <li>Copy world folder on the IO executor (async).</li>
 *   <li>Remove uid.dat and session.lock from clone.</li>
 *   <li>Register clone metadata in storage.</li>
 *   <li>Optionally load clone on main thread.</li>
 * </ol>
 */
public class CloneService {

    private final BlueprintPlugin plugin;
    private final BlueprintStorage storage;
    private final BlueprintConfig config;
    private final WorldService worldService;
    private final OperationLockService opLocks;
    private final Logger logger;

    public CloneService(
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
    }

    /**
     * Clones {@code sourceName} into a new world named {@code targetName}.
     *
     * @return a future that completes with the new world's metadata on success,
     *         or completes exceptionally on failure
     */
    public CompletableFuture<WorldMetadata> cloneWorld(
            @NotNull String sourceName,
            @NotNull String targetName,
            @Nullable UUID actor) {

        CompletableFuture<WorldMetadata> future = new CompletableFuture<>();

        // -- Validation (can run on any thread) --
        if (!config.isValidWorldName(targetName)) {
            future.completeExceptionally(new IllegalArgumentException("Invalid target world name: " + targetName));
            return future;
        }

        try {
            WorldMetadata sourceMeta = storage.getWorld(sourceName);
            if (sourceMeta == null) {
                future.completeExceptionally(new IllegalArgumentException("Source world not found: " + sourceName));
                return future;
            }
            if (storage.getWorld(targetName) != null) {
                future.completeExceptionally(new IllegalArgumentException("Target world already exists: " + targetName));
                return future;
            }

            // Acquire operation lock on both worlds
            if (!opLocks.tryLock(sourceName)) {
                future.completeExceptionally(new IllegalStateException("Source world '" + sourceName + "' is busy."));
                return future;
            }
            if (!opLocks.tryLock(targetName)) {
                opLocks.unlock(sourceName);
                future.completeExceptionally(new IllegalStateException("Target world '" + targetName + "' is busy."));
                return future;
            }

            boolean sourceWasOpen = sourceMeta.getStatus() == WorldStatus.OPEN
                    || sourceMeta.getStatus() == WorldStatus.LOCKED;

            // Close source on main thread before copying
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    if (sourceWasOpen) {
                        worldService.unloadBukkitWorld(sourceMeta);
                    }

                    // Dispatch file copy to IO executor
                    plugin.getIoExecutor().submit(() -> {
                        try {
                            performCopy(sourceMeta, targetName, actor, sourceWasOpen, future);
                        } catch (Exception e) {
                            opLocks.unlock(sourceName);
                            opLocks.unlock(targetName);
                            future.completeExceptionally(e);
                        }
                    });
                } catch (Exception e) {
                    opLocks.unlock(sourceName);
                    opLocks.unlock(targetName);
                    future.completeExceptionally(e);
                }
            });

        } catch (StorageException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    private void performCopy(
            @NotNull WorldMetadata sourceMeta,
            @NotNull String targetName,
            @Nullable UUID actor,
            boolean sourceWasOpen,
            @NotNull CompletableFuture<WorldMetadata> future) throws IOException, StorageException {

        Path worldContainer = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path sourceDir      = worldContainer.resolve(sourceMeta.getFolderName()).normalize();
        String targetFolder = config.getWorldFolderPrefix() + targetName;
        Path targetDir      = worldContainer.resolve(targetFolder).normalize();

        FileUtil.ensureInsideDirectory(worldContainer, sourceDir);
        FileUtil.ensureInsideDirectory(worldContainer, targetDir);

        logger.info("[Blueprint] Cloning '" + sourceMeta.getName() + "' -> '" + targetName + "'...");

        FileUtil.copyDirectory(sourceDir, targetDir);

        // Remove files that must not be shared between worlds
        FileUtil.deleteIfExists(targetDir.resolve("uid.dat"));
        FileUtil.deleteIfExists(targetDir.resolve("session.lock"));

        // Persist clone metadata
        Instant now = Instant.now();
        WorldMetadata cloneMeta = new WorldMetadata(
                targetName, targetFolder, actor,
                WorldStatus.CLOSED, now, now, null, null);
        storage.saveWorld(cloneMeta);
        storage.logAudit(AuditAction.CLONE_WORLD, targetName, actor,
                "source=" + sourceMeta.getName());

        logger.info("[Blueprint] Clone complete: '" + targetName + "'");

        // Re-open source world on main thread if it was open before
        // Then optionally open clone
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (sourceWasOpen) {
                    worldService.loadBukkitWorld(sourceMeta);
                    sourceMeta.setStatus(WorldStatus.OPEN);
                    storage.saveWorld(sourceMeta);
                }

                if (config.isAutoOpenClones()) {
                    worldService.loadBukkitWorld(cloneMeta);
                    cloneMeta.setStatus(WorldStatus.OPEN);
                    cloneMeta.markOpened();
                    storage.saveWorld(cloneMeta);
                }

                opLocks.unlock(sourceMeta.getName());
                opLocks.unlock(targetName);
                future.complete(cloneMeta);
            } catch (Exception e) {
                opLocks.unlock(sourceMeta.getName());
                opLocks.unlock(targetName);
                future.completeExceptionally(e);
            }
        });
    }
}
