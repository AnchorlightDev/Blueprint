package dev.anchorlight.blueprint.listener;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Blocks all nether and end portal travel server-wide to prevent players
 * from ending up in unmanaged locations outside Blueprint worlds.
 * Use /blueprint hub to return to the hub world instead.
 */
public class PortalListener implements Listener {

    private static final String PORTAL_BLOCKED_MSG =
            "<red>Portal travel is disabled. Use <yellow>/blueprint hub</yellow><red> to return to the hub.";

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(@NotNull PlayerPortalEvent event) {
        event.setCancelled(true);
        event.getPlayer().sendMessage(MiniMessage.miniMessage().deserialize(PORTAL_BLOCKED_MSG));
    }
}
