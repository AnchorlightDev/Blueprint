package dev.anchorlight.blueprint.command.subcommand;

import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HubCommand implements SubCommand {

    private final BlueprintConfig config;

    public HubCommand(@NotNull BlueprintConfig config) {
        this.config = config;
    }

    @Override public @NotNull String getName() { return "hub"; }
    @Override public @NotNull String getUsage() { return "hub"; }
    @Override public @NotNull String getDescription() { return "Return to the hub world"; }
    @Override public @NotNull String getPermission() { return "blueprint.command.hub"; }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, config, Messages.ERROR_PLAYER_ONLY);
            return;
        }
        if (!sender.hasPermission(getPermission())) {
            Messages.send(sender, config, Messages.ERROR_NO_PERMISSION);
            return;
        }

        String hubWorldName = config.getHubWorld();
        World hub = Bukkit.getWorld(hubWorldName);
        if (hub == null) {
            Messages.send(sender, config, "<red>Hub world <yellow>'" + hubWorldName + "'</yellow><red> is not loaded.");
            return;
        }

        player.teleport(hub.getSpawnLocation());
        Messages.send(sender, config, "<green>Teleported to the hub.");
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return List.of();
    }
}
