package dev.anchorlight.blueprint.service;

import dev.anchorlight.blueprint.BlueprintPlugin;
import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.database.BlueprintStorage;
import dev.anchorlight.blueprint.database.StorageException;
import dev.anchorlight.blueprint.model.AuditAction;
import dev.anchorlight.blueprint.model.WorldMetadata;
import dev.anchorlight.blueprint.model.WorldStatus;
import dev.anchorlight.blueprint.util.FileUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Core service that manages Blueprint world lifecycle:
 * create, open, close, lock, unlock, teleport, and delete.
 */
public class WorldService {

    private final BlueprintPlugin plugin;
    private final BlueprintStorage storage;
    private final BlueprintConfig config;
    private final OperationLockService opLocks;
    private final Logger logger;

    public WorldService(
            @NotNull BlueprintPlugin plugin,
            @NotNull BlueprintStorage storage,
            @NotNull BlueprintConfig config,
            @NotNull OperationLockService opLocks) {
        this.plugin  = plugin;
        this.storage = storage;
        this.config  = config;
        this.opLocks = opLocks;
        this.logger  = plugin.getLogger();
    }

    /**
     * Creates and registers a new managed world.
     * If {@code autoOpenCreatedWorlds} is true in config, the world is loaded on the main thread.
     *
     * @param name  logical world name (validated)
     * @param owner UUID of the creating player; null for console
     * @throws IllegalArgumentException if the name is invalid or already taken
     * @throws StorageException         on database failure
     */
    public WorldMetadata createWorld(@NotNull String name, @Nullable UUID owner) throws StorageException {
        if (!config.isValidWorldName(name)) {
            throw new IllegalArgumentException("Invalid world name: " + name);
        }
        if (storage.getWorld(name) != null) {
            throw new IllegalArgumentException("World already exists: " + name);
        }

        String folderName = config.getWorldFolderPrefix() + name;

        // Ensure no folder collision
        File worldFolder = new File(Bukkit.getWorldContainer(), folderName);
        if (worldFolder.exists()) {
            throw new IllegalArgumentException("World folder already exists on disk: " + folderName);
        }

        Instant now = Instant.now();
        WorldMetadata meta = new WorldMetadata(name, folderName, owner, WorldStatus.CLOSED, now, now, null, null);
        storage.saveWorld(meta);
        storage.logAudit(AuditAction.CREATE_WORLD, name, owner, "folder=" + folderName);

        if (config.isAutoOpenCreatedWorlds()) {
            // World creation must happen on the main thread
            if (Bukkit.isPrimaryThread()) {
                loadBukkitWorld(meta);
            } else {
                // Schedule on the main thread and wait (we are already async)
                plugin.getServer().getScheduler().runTask(plugin, () -> loadBukkitWorld(meta));
            }
        }

        return meta;
    }

    /**
     * Opens (loads) a closed world.
     *
     * <p>Must be called from the main server thread, or will be scheduled there.</p>
     */
    public void openWorld(@NotNull String name) throws StorageException {
        WorldMetadata meta = requireWorld(name);

        if (meta.getStatus() == WorldStatus.OPEN) {
            throw new IllegalStateException("World '" + name + "' is already open.");
        }

        ensureMainThread(() -> loadBukkitWorld(meta));

        meta.setStatus(WorldStatus.OPEN);
        meta.markOpened();
        storage.saveWorld(meta);
        storage.logAudit(AuditAction.OPEN_WORLD, name, null, null);
    }

    /**
     * Closes (unloads) an open world, evacuating players to the fallback world.
     */
    public void closeWorld(@NotNull String name) throws StorageException {
        WorldMetadata meta = requireWorld(name);

        if (meta.getStatus() == WorldStatus.CLOSED) {
            throw new IllegalStateException("World '" + name + "' is already closed.");
        }

        ensureMainThread(() -> unloadBukkitWorld(meta));

        meta.setStatus(WorldStatus.CLOSED);
        meta.markClosed();
        storage.saveWorld(meta);
        storage.logAudit(AuditAction.CLOSE_WORLD, name, null, null);
    }

    /**
     * Teleports a player to the spawn of the named world.
     * The world must be open (loaded).
     */
    public void teleport(@NotNull Player player, @NotNull String name) throws StorageException {
        WorldMetadata meta = requireWorld(name);

        if (meta.getStatus() == WorldStatus.CLOSED) {
            throw new IllegalStateException("World '" + name + "' is closed.");
        }

        World world = Bukkit.getWorld(meta.getFolderName());
        if (world == null) {
            throw new IllegalStateException("World '" + name + "' is not loaded.");
        }

        // Teleport must happen on main thread
        if (Bukkit.isPrimaryThread()) {
            player.teleport(world.getSpawnLocation());
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> player.teleport(world.getSpawnLocation()));
        }
    }

    /**
     * Locks a world, enabling ProtectionListener blocks.
     */
    public void lockWorld(@NotNull String name) throws StorageException {
        WorldMetadata meta = requireWorld(name);
        if (meta.getStatus() == WorldStatus.LOCKED) {
            throw new IllegalStateException("World '" + name + "' is already locked.");
        }

        // The world must be loaded to enforce protection; open it if closed
        if (meta.getStatus() == WorldStatus.CLOSED) {
            ensureMainThread(() -> loadBukkitWorld(meta));
        }

        meta.setStatus(WorldStatus.LOCKED);
        storage.saveWorld(meta);
        storage.logAudit(AuditAction.LOCK_WORLD, name, null, null);
    }

    /**
     * Unlocks a locked world, reverting it to OPEN status.
     */
    public void unlockWorld(@NotNull String name) throws StorageException {
        WorldMetadata meta = requireWorld(name);
        if (meta.getStatus() != WorldStatus.LOCKED) {
            throw new IllegalStateException("World '" + name + "' is not locked.");
        }

        meta.setStatus(WorldStatus.OPEN);
        storage.saveWorld(meta);
        storage.logAudit(AuditAction.UNLOCK_WORLD, name, null, null);
    }

    /**
     * Deletes a managed world: unloads it, deletes the folder async, removes metadata.
     * The caller must hold the operation lock for this world.
     *
     * @param actor UUID of the player or admin deleting the world; null for console
     */
    public void deleteWorld(@NotNull String name, @Nullable UUID actor) throws StorageException, IOException {
        WorldMetadata meta = requireWorld(name);

        // Unload world synchronously on the main thread first
        if (meta.getStatus() != WorldStatus.CLOSED) {
            ensureMainThread(() -> unloadBukkitWorld(meta));
        }

        Path worldPath = Bukkit.getWorldContainer().toPath().resolve(meta.getFolderName());
        FileUtil.ensureInsideDirectory(Bukkit.getWorldContainer().toPath(), worldPath);
        FileUtil.deleteDirectory(worldPath);

        storage.deleteWorld(name);
        storage.logAudit(AuditAction.DELETE_WORLD, name, actor, "folder=" + meta.getFolderName());
        logger.info("[Blueprint] Deleted world '" + name + "' (actor=" + actor + ")");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Fetches world metadata or throws a user-friendly exception. */
    public WorldMetadata requireWorld(@NotNull String name) throws StorageException {
        WorldMetadata meta = storage.getWorld(name);
        if (meta == null) {
            throw new IllegalArgumentException("World not found: " + name);
        }
        return meta;
    }

    /**
     * Loads the world into Bukkit.
     * <strong>Must be called on the main thread.</strong>
     */
    void loadBukkitWorld(@NotNull WorldMetadata meta) {
        World existing = Bukkit.getWorld(meta.getFolderName());
        if (existing != null) return; // already loaded

        WorldCreator creator = new WorldCreator(meta.getFolderName());
        // If this is a fresh world with no folder yet, use FLAT for build servers
        File folder = new File(Bukkit.getWorldContainer(), meta.getFolderName());
        if (!folder.exists()) {
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
        }
        creator.createWorld();
    }

    /**
     * Evacuates players and unloads the world.
     * <strong>Must be called on the main thread.</strong>
     */
    void unloadBukkitWorld(@NotNull WorldMetadata meta) {
        World world = Bukkit.getWorld(meta.getFolderName());
        if (world == null) return;

        // Move players to fallback world before unloading
        World fallback = Bukkit.getWorld(config.getFallbackWorld());
        if (fallback == null) fallback = Bukkit.getWorlds().get(0); // last resort

        for (Player p : world.getPlayers()) {
            p.teleport(fallback.getSpawnLocation());
            p.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<yellow>You were moved to the fallback world because '" + meta.getName() + "' was unloaded."));
        }

        Bukkit.unloadWorld(world, true); // save=true
    }

    /**
     * Runs {@code task} on the main thread.
     * If already on the main thread, runs immediately; otherwise schedules synchronously.
     * <p>Note: the "synchronous" scheduler variant blocks the calling thread, so we run
     * async tasks and schedule the Bukkit-API portion back on the main thread.</p>
     */
    private void ensureMainThread(@NotNull Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            // We can't block here without deadlock risk; callers in async context should
            // schedule back to the main thread themselves. This is a best-effort convenience.
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    public List<WorldMetadata> listWorlds(int page) throws StorageException {
        return storage.listWorlds(page, config.getPageSize());
    }
}
