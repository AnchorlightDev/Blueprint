package dev.anchorlight.blueprint.listener;

import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.database.BlueprintStorage;
import dev.anchorlight.blueprint.database.StorageException;
import dev.anchorlight.blueprint.model.WorldMetadata;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

/**
 * Handles player session lifecycle for Blueprint worlds.
 *
 * <p>When a player reconnects after having disconnected inside a Blueprint-managed
 * world, they are redirected to the fallback world's spawn. This prevents players
 * from re-entering a world that may have been closed, locked, or deleted while
 * they were offline, and provides consistent hub-respawn behaviour for build servers.</p>
 */
public class SessionListener implements Listener {

    private final Plugin plugin;
    private final BlueprintStorage storage;
    private final BlueprintConfig config;
    private final Logger logger;

    public SessionListener(
            @NotNull Plugin plugin,
            @NotNull BlueprintStorage storage,
            @NotNull BlueprintConfig config,
            @NotNull Logger logger) {
        this.plugin  = plugin;
        this.storage = storage;
        this.config  = config;
        this.logger  = logger;
    }

    /**
     * Runs one tick after join so that Bukkit has fully placed the player in the world
     * before we attempt a teleport. Teleporting inside PlayerJoinEvent itself is
     * unreliable on some Paper versions.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Delay one tick — player position is committed to the world by then
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            World currentWorld = player.getWorld();
            if (!isBlueprintWorld(currentWorld)) return;

            World fallback = Bukkit.getWorld(config.getFallbackWorld());
            if (fallback == null) {
                fallback = Bukkit.getWorlds().get(0);
            }

            if (fallback.equals(currentWorld)) return; // fallback IS the blueprint world (unusual config)

            final World dest = fallback;
            player.teleport(dest.getSpawnLocation());
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<yellow>You have been returned to spawn. Use <white>/bp tp <worldname></white> to re-enter your world."));
        });
    }

    /**
     * Returns true if the given Bukkit world is managed by Blueprint.
     * Uses the configured folder-prefix as a fast check, then confirms via DB.
     */
    private boolean isBlueprintWorld(@NotNull World world) {
        String folderName = world.getName();

        // Fast path: Blueprint worlds always use the configured prefix
        if (!folderName.startsWith(config.getWorldFolderPrefix())) return false;

        // Confirm via DB to avoid false-positives from non-Blueprint worlds
        // that happen to share the prefix (rare, but correct to check)
        try {
            for (WorldMetadata meta : storage.listAllWorlds()) {
                if (meta.getFolderName().equals(folderName)) return true;
            }
        } catch (StorageException e) {
            logger.warning("[Blueprint] SessionListener DB error: " + e.getMessage());
            // Fall back to prefix-only check if DB is unavailable
            return true;
        }
        return false;
    }
}
