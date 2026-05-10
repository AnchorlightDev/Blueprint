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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
     * The world must be OPEN or LOCKED. If it is marked open in the DB but not
     * actually loaded (e.g. after a server restart), it is auto-loaded first.
     */
    public void teleport(@NotNull Player player, @NotNull String name) throws StorageException {
        WorldMetadata meta = requireWorld(name);

        if (meta.getStatus() == WorldStatus.CLOSED) {
            throw new IllegalStateException("World '" + name + "' is closed.");
        }

        World world = Bukkit.getWorld(meta.getFolderName());
        if (world == null) {
            // DB says OPEN/LOCKED but Bukkit doesn't have it — recover by loading it now.
            // This can happen after a server restart since Blueprint worlds aren't in
            // server.properties and won't auto-load with Bukkit.
            if (!Bukkit.isPrimaryThread()) {
                // Must load on main thread; schedule and abort this call — the player
                // should re-issue the command. We inform them below.
                plugin.getServer().getScheduler().runTask(plugin, () -> loadBukkitWorld(meta));
                throw new IllegalStateException("World '" + name + "' was loading, try again in a moment.");
            }
            loadBukkitWorld(meta);
            world = Bukkit.getWorld(meta.getFolderName());
            if (world == null) {
                throw new IllegalStateException("World '" + name + "' could not be loaded.");
            }
        }

        final World finalWorld = world;
        if (Bukkit.isPrimaryThread()) {
            player.teleport(finalWorld.getSpawnLocation());
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> player.teleport(finalWorld.getSpawnLocation()));
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

        Path worldContainer = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path worldPath      = new File(Bukkit.getWorldContainer(), meta.getFolderName()).toPath().toAbsolutePath().normalize();
        FileUtil.ensureInsideDirectory(worldContainer, worldPath);
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
     * Loads the world into Bukkit and applies build-server defaults (no mobs).
     * <strong>Must be called on the main thread.</strong>
     */
    void loadBukkitWorld(@NotNull WorldMetadata meta) {
        World existing = Bukkit.getWorld(meta.getFolderName());
        if (existing != null) {
            applyBuildWorldRules(existing);
            return;
        }

        WorldCreator creator = new WorldCreator(meta.getFolderName());
        // Use FLAT for brand-new worlds (no existing folder); existing worlds keep their type.
        // Explicit generatorSettings avoids the "No key layers in MapLike[{}]" Paper 1.21 warning.
        File folder = new File(Bukkit.getWorldContainer(), meta.getFolderName());
        if (!folder.exists()) {
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            creator.generatorSettings(
                    "{\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1}," +
                    "{\"block\":\"minecraft:dirt\",\"height\":2}," +
                    "{\"block\":\"minecraft:grass_block\",\"height\":1}]," +
                    "\"biome\":\"minecraft:plains\"}");
        }
        World world = creator.createWorld();
        if (world != null) {
            applyBuildWorldRules(world);
        }
    }

    /**
     * Applies build-server world rules: disables mob spawning and weather changes
     * so freshly loaded or newly created worlds are immediately safe to build in.
     * <strong>Must be called on the main thread.</strong>
     */
    private void applyBuildWorldRules(@NotNull World world) {
        // Disable all mob spawning
        world.setSpawnFlags(false, false); // monsters=false, animals=false
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(org.bukkit.GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(org.bukkit.GameRule.DO_TRADER_SPAWNING, false);
        world.setGameRule(org.bukkit.GameRule.DO_INSOMNIA, false);       // no phantoms
        world.setGameRule(org.bukkit.GameRule.DISABLE_RAIDS, true);      // no raids
        world.setGameRule(org.bukkit.GameRule.DO_WARDEN_SPAWNING, false); // no wardens
        // Prevent mob griefing (creeper explosions, enderman block picking, etc.)
        world.setGameRule(org.bukkit.GameRule.MOB_GRIEFING, false);
    }

    /**
     * Re-loads all worlds that were OPEN or LOCKED when the server last stopped.
     * Call this during plugin startup after services are ready.
     * <strong>Must be called on the main thread.</strong>
     */
    public void restoreOpenWorlds() throws StorageException {
        List<WorldMetadata> all = storage.listAllWorlds();

        if (config.isCloseOnRestart()) {
            int count = 0;
            for (WorldMetadata meta : all) {
                if (meta.getStatus() == WorldStatus.OPEN || meta.getStatus() == WorldStatus.LOCKED) {
                    meta.setStatus(WorldStatus.CLOSED);
                    meta.markClosed();
                    storage.saveWorld(meta);
                    count++;
                }
            }
            if (count > 0) {
                logger.info("[Blueprint] Marked " + count + " world(s) as CLOSED on restart (close-on-restart=true).");
            }
            return;
        }

        int count = 0;
        for (WorldMetadata meta : all) {
            if (meta.getStatus() == WorldStatus.OPEN || meta.getStatus() == WorldStatus.LOCKED) {
                loadBukkitWorld(meta);
                count++;
            }
        }
        if (count > 0) {
            logger.info("[Blueprint] Restored " + count + " world(s) from previous session.");
        }
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

    /**
     * Renames a managed world: evacuates players, renames the world folder on disk,
     * updates the DB record (and all snapshot references) atomically, then reloads
     * the world if it was previously open.
     *
     * <p>Designed to be called from a background thread; Bukkit operations are
     * dispatched to the main thread and awaited.</p>
     *
     * @param actor UUID of the requesting player for the audit log; null for console
     * @return the new {@link WorldMetadata} record
     */
    public WorldMetadata renameWorld(@NotNull String oldName, @NotNull String newName, @Nullable UUID actor)
            throws StorageException, IOException {
        if (!config.isValidWorldName(newName)) {
            throw new IllegalArgumentException("Invalid world name: " + newName);
        }
        WorldMetadata meta = requireWorld(oldName);
        if (storage.getWorld(newName) != null) {
            throw new IllegalArgumentException("A world named '" + newName + "' already exists.");
        }

        String newFolderName = config.getWorldFolderPrefix() + newName;
        Path worldContainer  = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path oldFolder       = new File(Bukkit.getWorldContainer(), meta.getFolderName()).toPath().toAbsolutePath().normalize();
        Path newFolder       = new File(Bukkit.getWorldContainer(), newFolderName).toPath().toAbsolutePath().normalize();
        FileUtil.ensureInsideDirectory(worldContainer, newFolder);

        if (Files.exists(newFolder)) {
            throw new IllegalArgumentException("Folder '" + newFolderName + "' already exists on disk.");
        }

        boolean wasLoaded = meta.getStatus() != WorldStatus.CLOSED;

        // ── 1. Unload on the main thread (evacuates players) ──────────────
        if (wasLoaded) {
            runOnMainThreadAndWait(() -> unloadBukkitWorld(meta));
        }

        // ── 2. Rename folder on disk ───────────────────────────────────────
        try {
            Files.move(oldFolder, newFolder, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(oldFolder, newFolder);
        }

        // ── 3. Rename in DB (snapshots updated in the same transaction) ────
        storage.renameWorld(oldName, newName, newFolderName);
        storage.logAudit(AuditAction.RENAME_WORLD, newName, actor, "renamed-from=" + oldName);

        // ── 4. Reload on main thread if it was loaded ──────────────────────
        WorldMetadata newMeta = requireWorld(newName);
        if (wasLoaded) {
            if (Bukkit.isPrimaryThread()) {
                loadBukkitWorld(newMeta);
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () -> loadBukkitWorld(newMeta));
            }
        }

        logger.info("[Blueprint] Renamed world '" + oldName + "' -> '" + newName + "'");
        return newMeta;
    }

    /**
     * Runs {@code task} on the main thread and blocks the calling thread until it completes.
     * If already on the main thread, runs immediately.
     */
    private void runOnMainThreadAndWait(@NotNull Runnable task) throws IOException {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            task.run();
            done.complete(null);
        });
        try {
            done.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for main-thread task");
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("Main-thread task did not complete in time: " + e.getMessage());
        }
    }

    public List<WorldMetadata> listWorlds(int page) throws StorageException {
        return storage.listWorlds(page, config.getPageSize());
    }

    /**
     * Returns the logical names of all managed worlds, optionally filtered by status.
     * Pass no arguments to get every world regardless of status.
     * Results are sorted alphabetically.
     */
    public List<String> worldNames(WorldStatus... statuses) throws StorageException {
        Set<WorldStatus> filter = statuses.length > 0 ? Set.copyOf(Arrays.asList(statuses)) : Set.of();
        return storage.listAllWorlds().stream()
                .filter(m -> filter.isEmpty() || filter.contains(m.getStatus()))
                .map(WorldMetadata::getName)
                .sorted()
                .toList();
    }
}
