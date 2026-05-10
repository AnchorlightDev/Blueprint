package dev.anchorlight.blueprint.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Type-safe wrapper around Blueprint's config.yml values.
 * Reload by constructing a new instance after calling {@code plugin.reloadConfig()}.
 */
public class BlueprintConfig {

    private final String containerDirectory;
    private final String worldFolderPrefix;
    private final int pageSize;
    private final String fallbackWorld;
    private final String hubWorld;
    private final boolean autoOpenCreatedWorlds;
    private final boolean autoOpenClones;
    private final boolean closeOnRestart;

    private final boolean snapshotsEnabled;
    private final boolean autoBackupBeforeRestore;
    private final int maxSnapshotsPerWorld;

    private final boolean requireDeleteConfirmation;
    private final Pattern allowedWorldNamePattern;

    private final String messagePrefix;

    public BlueprintConfig(@NotNull FileConfiguration cfg) {
        containerDirectory    = cfg.getString("worlds.container-directory", "scaffold");
        worldFolderPrefix     = cfg.getString("worlds.folder-prefix", "blueprint_");
        pageSize              = cfg.getInt("worlds.page-size", 10);
        fallbackWorld         = cfg.getString("worlds.fallback-world", "world");
        hubWorld              = cfg.getString("worlds.hub-world", fallbackWorld);
        autoOpenCreatedWorlds = cfg.getBoolean("worlds.auto-open-created-worlds", true);
        autoOpenClones        = cfg.getBoolean("worlds.auto-open-clones", true);
        closeOnRestart        = cfg.getBoolean("worlds.close-on-restart", false);

        snapshotsEnabled           = cfg.getBoolean("snapshots.enabled", true);
        autoBackupBeforeRestore    = cfg.getBoolean("snapshots.auto-backup-before-restore", true);
        maxSnapshotsPerWorld       = cfg.getInt("snapshots.max-per-world", 25);

        requireDeleteConfirmation  = cfg.getBoolean("safety.require-delete-confirmation", true);
        String regex               = cfg.getString("safety.allowed-world-name-regex", "^[a-z0-9_-]{3,32}$");
        allowedWorldNamePattern    = Pattern.compile(regex);

        messagePrefix = cfg.getString("messages.prefix",
                "<gradient:#00aaff:#0066ff><bold>Blueprint</bold></gradient> <dark_gray>»</dark_gray> ");
    }

    public String getContainerDirectory() { return containerDirectory; }
    public String getWorldFolderPrefix() { return worldFolderPrefix; }
    public int getPageSize() { return pageSize; }
    public String getFallbackWorld() { return fallbackWorld; }
    public String getHubWorld() { return hubWorld; }
    public boolean isAutoOpenCreatedWorlds() { return autoOpenCreatedWorlds; }
    public boolean isAutoOpenClones() { return autoOpenClones; }
    public boolean isCloseOnRestart() { return closeOnRestart; }

    public boolean isSnapshotsEnabled() { return snapshotsEnabled; }
    public boolean isAutoBackupBeforeRestore() { return autoBackupBeforeRestore; }
    public int getMaxSnapshotsPerWorld() { return maxSnapshotsPerWorld; }

    public boolean isRequireDeleteConfirmation() { return requireDeleteConfirmation; }
    public Pattern getAllowedWorldNamePattern() { return allowedWorldNamePattern; }

    public String getMessagePrefix() { return messagePrefix; }

    /** Returns true if the given name passes the configured regex. */
    public boolean isValidWorldName(@NotNull String name) {
        return allowedWorldNamePattern.matcher(name).matches();
    }
}
