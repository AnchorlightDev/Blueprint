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
        storage = new YamlBlueprintStorage(getDataFolder(), getLogger());
        try {
            storage.initializeSchema();
        } catch (StorageException e) {
            getLogger().severe("Failed to initialize storage: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // ── 3b. Verify world-container ────────────────────────────────────
        // Blueprint requires bukkit.yml  →  settings:  world-container: blueprint
        // so that Bukkit.getWorldContainer() returns the blueprint/ sub-folder and
        // WorldCreator("myworld") creates worlds at blueprint/myworld/ directly.
        // Without this, worlds land in the server root under arbitrary names.
        String expectedContainer = blueprintConfig.getContainerDirectory();
        File   worldContainer    = getServer().getWorldContainer();
        if (!worldContainer.getName().equals(expectedContainer)) {
            getLogger().warning("[Blueprint] *** SETUP REQUIRED ***");
            getLogger().warning("[Blueprint] Blueprint worlds should live at ./" + expectedContainer + "/<name>/");
            getLogger().warning("[Blueprint] Add the following to bukkit.yml and restart:");
            getLogger().warning("[Blueprint]   settings:");
            getLogger().warning("[Blueprint]     world-container: " + expectedContainer);
            getLogger().warning("[Blueprint] Without this, worlds may be placed in an unexpected location.");
        } else {
            getLogger().info("[Blueprint] World container: " + worldContainer.getAbsolutePath());
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

    /**
     * Returns the Bukkit folder name for a Blueprint world.
     * With {@code settings.world-container: blueprint} in bukkit.yml, this is
     * the plain world name — Bukkit maps it to {@code blueprint/<name>/} on disk.
     */
    public String worldFolderName(String name) {
        return name;
    }
}
