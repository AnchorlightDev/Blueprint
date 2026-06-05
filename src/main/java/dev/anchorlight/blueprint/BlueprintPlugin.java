package dev.anchorlight.blueprint;

import dev.anchorlight.blueprint.command.BlueprintCommand;
import dev.anchorlight.blueprint.command.subcommand.*;
import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.database.BlueprintStorage;
import dev.anchorlight.blueprint.database.YamlBlueprintStorage;
import dev.anchorlight.blueprint.database.StorageException;
import dev.anchorlight.blueprint.listener.PortalListener;
import dev.anchorlight.blueprint.listener.ProtectionListener;
import dev.anchorlight.blueprint.listener.SessionListener;
import dev.anchorlight.blueprint.service.CloneService;
import dev.anchorlight.blueprint.service.OperationLockService;
import dev.anchorlight.blueprint.service.SnapshotService;
import dev.anchorlight.blueprint.service.WorldService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Blueprint – modern build-server world management plugin for Paper.
 *
 * <p>Bootstrap order:
 * <ol>
 *   <li>Save default config</li>
 *   <li>Initialize storage</li>
 *   <li>Redirect world container to blueprint/ subfolder (via reflection)</li>
 *   <li>Initialize services</li>
 *   <li>Register commands</li>
 *   <li>Register listeners</li>
 * </ol>
 */
public class BlueprintPlugin extends JavaPlugin {

    private BlueprintConfig blueprintConfig;
    private BlueprintStorage storage;
    private WorldService worldService;
    private CloneService cloneService;
    private SnapshotService snapshotService;
    private OperationLockService opLocks;

    /** Single-threaded executor for all async file I/O. */
    private ExecutorService ioExecutor;

    /**
     * True when the world container was successfully redirected to
     * {@code <serverRoot>/blueprint/} via reflection. When false, worlds fall
     * back to flat {@code blueprint_<name>} names in the server root.
     */
    private boolean worldContainerSet = false;

    @Override
    public void onEnable() {
        // ── 1. Configuration ──────────────────────────────────────────────
        saveDefaultConfig();
        blueprintConfig = new BlueprintConfig(getConfig());

        // ── 2. Data folder ────────────────────────────────────────────────
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Failed to create plugin data folder! Disabling Blueprint.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Snapshot folder
        File snapshotDir = new File(getDataFolder(), "snapshots");
        if (!snapshotDir.exists() && !snapshotDir.mkdirs()) {
            getLogger().warning("Could not create snapshots directory.");
        }

        // ── 3. Storage ────────────────────────────────────────────────────
        storage = new YamlBlueprintStorage(getDataFolder(), getLogger(), blueprintConfig.getContainerDirectory());
        try {
            storage.initializeSchema();
        } catch (StorageException e) {
            getLogger().severe("Failed to initialize storage: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // ── 3b. World container ───────────────────────────────────────────
        // Route Blueprint worlds into <serverRoot>/blueprint/ so paths are
        // predictable (/server/blueprint/<name>/) and don't trigger Paper's
        // dimension routing (which fires for any WorldCreator name with '/').
        //
        // Server.setWorldContainer was removed from the Bukkit interface but
        // CraftServer still carries the method — reach it via reflection.
        // If unavailable, fall back to a "blueprint_" prefix at the server root.
        File worldContainerDir = new File(getServer().getWorldContainer(),
                blueprintConfig.getContainerDirectory());
        if (!worldContainerDir.exists() && !worldContainerDir.mkdirs()) {
            getLogger().warning("[Blueprint] Could not create world container directory: " + worldContainerDir);
        }
        try {
            getServer().getClass()
                    .getMethod("setWorldContainer", File.class)
                    .invoke(getServer(), worldContainerDir);
            worldContainerSet = true;
            getLogger().info("[Blueprint] World container: " + worldContainerDir.getAbsolutePath());
        } catch (NoSuchMethodException e) {
            getLogger().warning("[Blueprint] setWorldContainer unavailable in this Paper build — "
                    + "worlds will use '" + blueprintConfig.getWorldFolderPrefix() + "<name>' naming at server root.");
        } catch (Exception e) {
            getLogger().warning("[Blueprint] Could not redirect world container ("
                    + e.getClass().getSimpleName() + ") — using prefix fallback.");
        }

        // ── 4. Services ───────────────────────────────────────────────────
        ioExecutor     = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Blueprint-IO");
            t.setDaemon(true);
            return t;
        });
        opLocks        = new OperationLockService();
        worldService   = new WorldService(this, storage, blueprintConfig, opLocks);
        cloneService   = new CloneService(this, storage, blueprintConfig, worldService, opLocks);
        snapshotService = new SnapshotService(this, storage, blueprintConfig, worldService, opLocks);

        // ── 5. Commands ───────────────────────────────────────────────────
        BlueprintCommand dispatcher = new BlueprintCommand(blueprintConfig);
        dispatcher.register(new HelpCommand(dispatcher, blueprintConfig));
        dispatcher.register(new VersionCommand(this, blueprintConfig));
        dispatcher.register(new CreateCommand(worldService, blueprintConfig, getLogger()));
        dispatcher.register(new ImportCommand(worldService, blueprintConfig, getLogger()));
        dispatcher.register(new ListCommand(worldService, blueprintConfig, getLogger()));
        dispatcher.register(new TpCommand(worldService, blueprintConfig, getLogger()));
        dispatcher.register(new HubCommand(blueprintConfig));
        dispatcher.register(new OpenCommand(worldService, blueprintConfig, getLogger()));
        dispatcher.register(new CloseCommand(worldService, blueprintConfig, getLogger()));
        dispatcher.register(new LockCommand(worldService, blueprintConfig, getLogger()));
        dispatcher.register(new UnlockCommand(worldService, blueprintConfig, getLogger()));
        dispatcher.register(new CloneCommand(cloneService, worldService, blueprintConfig, getLogger()));
        dispatcher.register(new SnapshotCommand(snapshotService, worldService, blueprintConfig, getLogger()));
        dispatcher.register(new RenameCommand(worldService, opLocks, blueprintConfig, getLogger()));
        dispatcher.register(new DeleteCommand(worldService, opLocks, blueprintConfig, getLogger()));

        PluginCommand cmd = getCommand("blueprint");
        if (cmd != null) {
            cmd.setExecutor(dispatcher);
            cmd.setTabCompleter(dispatcher);
        } else {
            getLogger().severe("Could not register /blueprint command! Check plugin.yml.");
        }

        // ── 6. Listeners ──────────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(
                new ProtectionListener(storage, getLogger()), this);
        getServer().getPluginManager().registerEvents(new PortalListener(), this);
        getServer().getPluginManager().registerEvents(
                new SessionListener(this, storage, blueprintConfig, getLogger()), this);

        // ── 7. Restore world state from previous session ──────────────────
        try {
            worldService.restoreOpenWorlds();
        } catch (StorageException e) {
            getLogger().warning("[Blueprint] Could not restore world state on startup: " + e.getMessage());
        }

        // ── 8. Done ───────────────────────────────────────────────────────
        getLogger().info("Blueprint v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (ioExecutor != null && !ioExecutor.isShutdown()) {
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    getLogger().warning("IO executor did not terminate cleanly within 10s; forcing shutdown.");
                    ioExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ioExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (storage != null) {
            storage.close();
        }

        getLogger().info("Blueprint disabled.");
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public BlueprintConfig getBlueprintConfig() { return blueprintConfig; }
    public BlueprintStorage getStorage() { return storage; }
    public WorldService getWorldService() { return worldService; }
    public CloneService getCloneService() { return cloneService; }
    public SnapshotService getSnapshotService() { return snapshotService; }
    public OperationLockService getOpLocks() { return opLocks; }
    public ExecutorService getIoExecutor() { return ioExecutor; }

    /** Whether the world container was successfully redirected to blueprint/. */
    public boolean isWorldContainerSet() { return worldContainerSet; }

    /**
     * Returns the Bukkit folder name for a Blueprint world.
     * <ul>
     *   <li>When the world container is redirected to {@code blueprint/}:
     *       returns just {@code name} → world lives at {@code blueprint/<name>/}.</li>
     *   <li>Otherwise: returns {@code blueprint_<name>} → world lives at
     *       {@code <serverRoot>/blueprint_<name>/} (flat fallback).</li>
     * </ul>
     */
    public String worldFolderName(String name) {
        return worldContainerSet ? name : blueprintConfig.getWorldFolderPrefix() + name;
    }
}
