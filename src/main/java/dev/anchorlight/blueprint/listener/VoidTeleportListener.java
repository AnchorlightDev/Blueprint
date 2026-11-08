package dev.anchorlight.blueprint.listener;

import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.database.BlueprintStorage;
import dev.anchorlight.blueprint.database.StorageException;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

/**
 * Rescues players who fall into the void of a Blueprint build world.
 *
 * <p>Blueprint worlds use a {@link dev.anchorlight.blueprint.util.VoidGenerator void generator},
 * so a player who walks off the spawn platform falls forever. When a player drops below the
 * configured {@code worlds.void-teleport.min-y}, they are teleported back to that world's spawn.</p>
 *
 * <p>Only Blueprint-managed worlds are affected, so the main world and any normal worlds (which
 * have legitimate terrain far below Y=-10) are never touched.</p>
 */
public class VoidTeleportListener implements Listener {

    private final BlueprintStorage storage;
    private final BlueprintConfig config;
    private final Logger logger;

    public VoidTeleportListener(@NotNull BlueprintStorage storage,
                                @NotNull BlueprintConfig config,
                                @NotNull Logger logger) {
        this.storage = storage;
        this.config  = config;
        this.logger  = logger;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.isVoidTeleportEnabled()) return;

        Location to = event.getTo();
        // Cheap early-out: PlayerMoveEvent fires constantly, so do nothing until the player
        // is actually below the threshold. The (relatively expensive) world lookup below only
        // runs in that rare case.
        if (to == null || to.getY() >= config.getVoidTeleportMinY()) return;

        World world = to.getWorld();
        if (world == null) return;
        if (!isBlueprintWorld(world)) return;

        Location spawn = world.getSpawnLocation();
        // Redirect the move itself so the player never actually reaches the void, preserving
        // their facing direction. teleport() would also work but setTo avoids a second event.
        spawn.setYaw(to.getYaw());
        spawn.setPitch(to.getPitch());
        event.setTo(spawn);

        Player player = event.getPlayer();
        player.setFallDistance(0f);
    }

    private boolean isBlueprintWorld(@NotNull World world) {
        try {
            return storage.getWorldByFolder(world.getName()) != null;
        } catch (StorageException e) {
            logger.warning("[Blueprint] VoidTeleportListener storage error: " + e.getMessage());
            return false;
        }
    }
}
