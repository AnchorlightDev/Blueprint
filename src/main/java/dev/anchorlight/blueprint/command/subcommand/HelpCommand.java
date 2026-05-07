package dev.anchorlight.blueprint.command.subcommand;

import dev.anchorlight.blueprint.command.BlueprintCommand;
import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.util.Messages;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HelpCommand implements SubCommand {

    private final BlueprintCommand dispatcher;
    private final BlueprintConfig config;

    public HelpCommand(@NotNull BlueprintCommand dispatcher, @NotNull BlueprintConfig config) {
        this.dispatcher = dispatcher;
        this.config     = config;
    }

    @Override public @NotNull String getName() { return "help"; }
    @Override public @NotNull String getUsage() { return "help [page]"; }
    @Override public @NotNull String getDescription() { return "Show this help menu"; }
    @Override public @NotNull String getPermission() { return "blueprint.command.help"; }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<newline>" + config.getMessagePrefix() + "<aqua>Available commands:</newline>"));

        for (SubCommand cmd : dispatcher.getSubCommands()) {
            if (!cmd.getPermission().isEmpty() && !sender.hasPermission(cmd.getPermission())) continue;
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "  <gray>/blueprint <yellow>" + cmd.getUsage() + " <dark_gray>- <white>" + cmd.getDescription()));
        }
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<newline>"));
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return List.of();
    }
}
