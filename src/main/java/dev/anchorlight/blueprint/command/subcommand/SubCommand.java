package dev.anchorlight.blueprint.command.subcommand;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Contract for all Blueprint sub-commands.
 */
public interface SubCommand {

    /** Primary sub-command name, lowercase (e.g. "create"). */
    @NotNull String getName();

    /** Usage hint shown in /blueprint help (e.g. "create <world>"). */
    @NotNull String getUsage();

    /** Short description shown in /blueprint help. */
    @NotNull String getDescription();

    /** Permission node required to run this sub-command; empty string = no permission. */
    @NotNull String getPermission();

    /** Executes the sub-command. Arguments do NOT include the sub-command name itself. */
    void execute(@NotNull CommandSender sender, @NotNull String[] args);

    /** Tab-completion for arguments. Return empty list for no suggestions. */
    @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args);
}
