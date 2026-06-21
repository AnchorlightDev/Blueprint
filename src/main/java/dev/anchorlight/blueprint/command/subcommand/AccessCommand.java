package dev.anchorlight.blueprint.command.subcommand;

import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.database.StorageException;
import dev.anchorlight.blueprint.model.WorldMetadata;
import dev.anchorlight.blueprint.service.WorldService;
import dev.anchorlight.blueprint.util.Messages;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Controls per-world access: a world is either <em>community</em> (open to
 * everyone) or <em>restricted</em> (only players with
 * {@link WorldService#ACCESS_RESTRICTED_PERM} may teleport in).
 *
 * <p>Usage:</p>
 * <ul>
 *   <li>{@code /bp access <world>} — show the world's current access level</li>
 *   <li>{@code /bp access <world> community} — open it to everyone</li>
 *   <li>{@code /bp access <world> restricted} — lock it to the build team</li>
 * </ul>
 */
public class AccessCommand implements SubCommand {

    private final WorldService worldService;
    private final BlueprintConfig config;
    private final Logger logger;

    public AccessCommand(@NotNull WorldService worldService, @NotNull BlueprintConfig config, @NotNull Logger logger) {
        this.worldService = worldService;
        this.config       = config;
        this.logger       = logger;
    }

    @Override public @NotNull String getName() { return "access"; }
    @Override public @NotNull String getUsage() { return "access <world> [community|restricted]"; }
    @Override public @NotNull String getDescription() { return "View or set who can enter a world"; }
    @Override public @NotNull String getPermission() { return "blueprint.command.access"; }

    @Override
    public void execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            Messages.send(sender, config, Messages.ERROR_NO_PERMISSION);
            return;
        }
        if (args.length < 1) {
            Messages.send(sender, config, "<red>Usage: <yellow>/blueprint " + getUsage());
            return;
        }

        String worldName = args[0].toLowerCase();
        try {
            // No level given: report the current access level.
            if (args.length < 2) {
                WorldMetadata meta = worldService.requireWorld(worldName);
                if (meta.isRestricted()) {
                    Messages.send(sender, config, "<yellow>'" + worldName
                            + "'<gray> is <light_purple>restricted<gray> (build team only).");
                } else {
                    Messages.send(sender, config, "<yellow>'" + worldName
                            + "'<gray> is <green>community<gray> (open to everyone).");
                }
                return;
            }

            boolean restricted;
            switch (args[1].toLowerCase()) {
                case "restricted", "team", "private", "lock" -> restricted = true;
                case "community", "public", "open", "unlock" -> restricted = false;
                default -> {
                    Messages.send(sender, config,
                            "<red>Unknown access level <yellow>'" + args[1]
                            + "'<red>. Use <yellow>community<red> or <yellow>restricted<red>.");
                    return;
                }
            }

            worldService.setRestricted(worldName, restricted,
                    sender instanceof org.bukkit.entity.Player p ? p.getUniqueId() : null);

            if (restricted) {
                Messages.send(sender, config, "<light_purple>World <yellow>'" + worldName
                        + "'<light_purple> is now <bold>restricted</bold> — only players with <yellow>"
                        + WorldService.ACCESS_RESTRICTED_PERM + "<light_purple> can enter.");
            } else {
                Messages.send(sender, config, "<green>World <yellow>'" + worldName
                        + "'<green> is now <bold>community</bold> — open to everyone.");
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            Messages.send(sender, config, "<red>" + e.getMessage());
        } catch (StorageException e) {
            logger.severe("[Blueprint] DB error setting world access: " + e.getMessage());
            Messages.send(sender, config, Messages.ERROR_DB_FAILURE);
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            try { return worldService.worldNames(); }
            catch (StorageException e) { return List.of(); }
        }
        if (args.length == 2) {
            return Stream.of("community", "restricted")
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
