package dev.anchorlight.blueprint.listener;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Disables all portal activity server-wide:
 * - Players cannot travel through nether or end portals.
 * - Entities (mobs, items) cannot travel through portals.
 * - Nether portal frames cannot be activated (lit).
 */
public class PortalListener implements Listener {

    private static final String PORTAL_BLOCKED_MSG =
            "<red>Portal travel is disabled. Use <yellow>/blueprint hub</yellow><red> to return to the hub.";

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(@NotNull PlayerPortalEvent event) {
        event.setCancelled(true);
        event.getPlayer().sendMessage(MiniMessage.miniMessage().deserialize(PORTAL_BLOCKED_MSG));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(@NotNull EntityPortalEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(@NotNull PortalCreateEvent event) {
        event.setCancelled(true);
    }
}
