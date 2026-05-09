package dev.anchorlight.blueprint.command.subcommand;

import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.database.StorageException;
import dev.anchorlight.blueprint.service.CloneService;
import dev.anchorlight.blueprint.service.WorldService;
import dev.anchorlight.blueprint.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class CloneCommand implements SubCommand {

    private final CloneService cloneService;
    private final WorldService worldService;
    private final BlueprintConfig config;
    private final Logger logger;

    public CloneCommand(@NotNull CloneService cloneService, @NotNull WorldService worldService,
                        @NotNull BlueprintConfig config, @NotNull Logger logger) {
        this.cloneService = cloneService;
        this.worldService = worldService;
        this.config       = config;
        this.logger       = logger;
    }

    @Override public @NotNull String getName() { return "clone"; }
    @Override public @NotNull String getUsage() { return "clone <source> <target>"; }
    @Override public @NotNull String getDescription() { return "Clone a world into a new world"; }
    @Override public @NotNull String getPermission() { return "blueprint.command.clone"; }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            Messages.send(sender, config, Messages.ERROR_NO_PERMISSION);
            return;
        }
        if (args.length < 2) {
            Messages.send(sender, config, "<red>Usage: <yellow>/blueprint " + getUsage());
            return;
        }

        String source = args[0].toLowerCase();
        String target = args[1].toLowerCase();
        UUID actor    = sender instanceof Player p ? p.getUniqueId() : null;

        Messages.send(sender, config, "<gray>Cloning <yellow>'" + source + "'<gray> → <yellow>'" + target + "'<gray>...");

        cloneService.cloneWorld(source, target, actor).whenComplete((meta, ex) -> {
            if (ex != null) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof IllegalArgumentException || cause instanceof IllegalStateException) {
                    Messages.send(sender, config, "<red>" + cause.getMessage());
                } else {
                    logger.severe("[Blueprint] Clone error: " + cause.getMessage());
                    Messages.send(sender, config, Messages.ERROR_IO_FAILURE);
                }
            } else {
                Messages.send(sender, config, "<green>Clone complete! World <yellow>'" + target + "'<green> is ready.");
            }
        });
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            try { return worldService.worldNames(); }
            catch (StorageException e) { return List.of(); }
        }
        return List.of();
    }
}
