package dev.anchorlight.blueprint.command;

import dev.anchorlight.blueprint.command.subcommand.SubCommand;
import dev.anchorlight.blueprint.config.BlueprintConfig;
import dev.anchorlight.blueprint.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Root dispatcher for /blueprint (alias /bp).
 * Delegates to registered {@link SubCommand} instances based on the first argument.
 */
public class BlueprintCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommandMap = new LinkedHashMap<>();
    private final BlueprintConfig config;

    public BlueprintCommand(@NotNull BlueprintConfig config) {
        this.config = config;
    }

    /** Registers a sub-command; call before the command is ever dispatched. */
    public void register(@NotNull SubCommand cmd) {
        subCommandMap.put(cmd.getName().toLowerCase(), cmd);
    }

    /** Returns all registered sub-commands in registration order. */
    public @NotNull Collection<SubCommand> getSubCommands() {
        return Collections.unmodifiableCollection(subCommandMap.values());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            // Default to help
            SubCommand help = subCommandMap.get("help");
            if (help != null) help.execute(sender, new String[0]);
            return true;
        }

        String subName = args[0].toLowerCase();
        SubCommand sub = subCommandMap.get(subName);

        if (sub == null) {
            Messages.send(sender, config,
                    "<red>Unknown sub-command. Use <yellow>/blueprint help<red> for a list.");
            return true;
        }

        // Slice arguments, removing the sub-command name
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        sub.execute(sender, subArgs);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            // Suggest sub-command names the sender has permission for
            List<String> suggestions = new ArrayList<>();
            String partial = args[0].toLowerCase();
            for (SubCommand sub : subCommandMap.values()) {
                if (sub.getName().startsWith(partial)) {
                    if (sub.getPermission().isEmpty() || sender.hasPermission(sub.getPermission())) {
                        suggestions.add(sub.getName());
                    }
                }
            }
            return suggestions;
        }

        if (args.length > 1) {
            SubCommand sub = subCommandMap.get(args[0].toLowerCase());
            if (sub != null) {
                return sub.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
            }
        }

        return List.of();
    }
}
