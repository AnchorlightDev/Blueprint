package dev.anchorlight.blueprint.command.subcommand;

import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.util.Messages;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VersionCommand implements SubCommand {

    private final Plugin plugin;
    private final BlueprintConfig config;

    public VersionCommand(@NotNull Plugin plugin, @NotNull BlueprintConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override public @NotNull String getName() { return "version"; }
    @Override public @NotNull String getUsage() { return "version"; }
    @Override public @NotNull String getDescription() { return "Show Blueprint version info"; }
    @Override public @NotNull String getPermission() { return "blueprint.command.version"; }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        String ver = plugin.getDescription().getVersion();
        Messages.send(sender, config,
                "<aqua>Blueprint <white>v" + ver + " <gray>by <white>Anchorlight");
        Messages.send(sender, config,
                "<gray>Running on <white>" + plugin.getServer().getVersion());
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return List.of();
    }
}
