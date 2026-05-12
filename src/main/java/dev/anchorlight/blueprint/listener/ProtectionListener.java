package dev.anchorlight.blueprint.listener;

import dev.anchorlight.blueprint.database.BlueprintStorage;
import dev.anchorlight.blueprint.database.StorageException;
import dev.anchorlight.blueprint.model.WorldMetadata;
import dev.anchorlight.blueprint.model.WorldStatus;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

/**
 * Enforces block/interaction protection for worlds in the {@link WorldStatus#LOCKED} state.
 * Players with {@code blueprint.bypass.lock} are exempt.
 */
public class ProtectionListener implements Listener {

    private static final String BYPASS_PERM = "blueprint.bypass.lock";
    private static final String LOCKED_MSG  =
            "<red>This world is <bold>locked</bold>. You cannot modify it.";

    private final BlueprintStorage storage;
    private final Logger logger;

    public ProtectionListener(@NotNull BlueprintStorage storage, @NotNull Logger logger) {
        this.storage = storage;
        this.logger  = logger;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isLockedForPlayer(event.getBlock().getWorld(), event.getPlayer())) {
            event.setCancelled(true);
            sendLocked(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isLockedForPlayer(event.getBlock().getWorld(), event.getPlayer())) {
            event.setCancelled(true);
            sendLocked(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (isLockedForPlayer(event.getEntity().getWorld(), player)) {
            event.setCancelled(true);
            sendLocked(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isLockedForPlayer(event.getBlock().getWorld(), event.getPlayer())) {
            event.setCancelled(true);
            sendLocked(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (isLockedForPlayer(event.getBlock().getWorld(), event.getPlayer())) {
            event.setCancelled(true);
            sendLocked(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        if (isWorldLocked(event.getEntity().getWorld())) {
            event.blockList().clear(); // Explosion still happens but leaves no block damage
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private boolean isLockedForPlayer(@NotNull World world, @NotNull Player player) {
        if (player.hasPermission(BYPASS_PERM)) return false;
        return isWorldLocked(world);
    }

    private boolean isWorldLocked(@NotNull World world) {
        try {
            // Try to match world folder name to a Blueprint world
            // In modern Paper, world.getName() usually returns the folder name relative to container
            String folderName = world.getName();

            // For worlds in a container, Paper might name them like "blueprint/worldname"
            // storage.getWorldByFolder(folderName) should handle this if folderName is "container/world"
            WorldMetadata meta = storage.getWorldByFolder(folderName);
            return meta != null && meta.getStatus() == WorldStatus.LOCKED;
        } catch (StorageException e) {
            logger.warning("[Blueprint] ProtectionListener storage error: " + e.getMessage());
            return false;
        }
    }

    private void sendLocked(@NotNull Player player) {
        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(LOCKED_MSG));
    }
}
