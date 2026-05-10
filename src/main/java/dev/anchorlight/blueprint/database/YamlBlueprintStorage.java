package dev.anchorlight.blueprint.database;

import dev.anchorlight.blueprint.model.AuditAction;
import dev.anchorlight.blueprint.model.SnapshotMetadata;
import dev.anchorlight.blueprint.model.WorldMetadata;
import dev.anchorlight.blueprint.model.WorldStatus;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * YAML-based implementation of {@link BlueprintStorage}.
 * Stores world metadata in a 'blueprint.yml' file within each world folder.
 * Snapshots are listed by scanning the snapshots directory.
 */
public class YamlBlueprintStorage implements BlueprintStorage {

    private final File containerDir;
    private final File snapshotRootDir;
    private final Logger logger;

    public YamlBlueprintStorage(@NotNull File containerDir, @NotNull File snapshotRootDir, @NotNull Logger logger) {
        this.containerDir = containerDir;
        this.snapshotRootDir = snapshotRootDir;
        this.logger = logger;
    }

    @Override
    public void initializeSchema() throws StorageException {
        if (!containerDir.exists() && !containerDir.mkdirs()) {
            throw new StorageException("Could not create container directory: " + containerDir.getAbsolutePath());
        }
    }

    @Override
    public void close() {
        // No-op for YAML
    }

    @Override
    public void saveWorld(@NotNull WorldMetadata meta) throws StorageException {
        File worldDir = new File(containerDir, meta.getName());
        if (!worldDir.exists() && !worldDir.mkdirs()) {
            throw new StorageException("Could not create world directory: " + worldDir.getAbsolutePath());
        }

        File configFile = new File(worldDir, "blueprint.yml");
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("name", meta.getName());
        yaml.set("folder_name", meta.getFolderName());
        yaml.set("owner_uuid", meta.getOwnerUuid() != null ? meta.getOwnerUuid().toString() : null);
        yaml.set("status", meta.getStatus().name());
        yaml.set("created_at", meta.getCreatedAt().toEpochMilli());
        yaml.set("updated_at", meta.getUpdatedAt().toEpochMilli());
        yaml.set("last_opened_at", meta.getLastOpenedAt() != null ? meta.getLastOpenedAt().toEpochMilli() : null);
        yaml.set("last_closed_at", meta.getLastClosedAt() != null ? meta.getLastClosedAt().toEpochMilli() : null);

        try {
            yaml.save(configFile);
        } catch (IOException e) {
            throw new StorageException("Failed to save world metadata to " + configFile.getAbsolutePath(), e);
        }
    }

    @Override
    public @Nullable WorldMetadata getWorld(@NotNull String name) throws StorageException {
        File worldDir = new File(containerDir, name);
        File configFile = new File(worldDir, "blueprint.yml");
        if (!configFile.exists()) {
            return null;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        try {
            String ownerUuidStr = yaml.getString("owner_uuid");
            Long lastOpenedAt = yaml.contains("last_opened_at") ? yaml.getLong("last_opened_at") : null;
            Long lastClosedAt = yaml.contains("last_closed_at") ? yaml.getLong("last_closed_at") : null;

            return new WorldMetadata(
                    yaml.getString("name"),
                    yaml.getString("folder_name"),
                    ownerUuidStr != null ? UUID.fromString(ownerUuidStr) : null,
                    WorldStatus.valueOf(yaml.getString("status", "CLOSED")),
                    Instant.ofEpochMilli(yaml.getLong("created_at")),
                    Instant.ofEpochMilli(yaml.getLong("updated_at")),
                    lastOpenedAt != null && lastOpenedAt != 0 ? Instant.ofEpochMilli(lastOpenedAt) : null,
                    lastClosedAt != null && lastClosedAt != 0 ? Instant.ofEpochMilli(lastClosedAt) : null
            );
        } catch (Exception e) {
            logger.warning("Failed to load world metadata from " + configFile.getAbsolutePath() + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public List<WorldMetadata> listWorlds(int page, int pageSize) throws StorageException {
        List<WorldMetadata> all = listAllWorlds();
        all.sort(Comparator.comparing(WorldMetadata::getCreatedAt).reversed());

        int start = (page - 1) * pageSize;
        if (start >= all.size()) return Collections.emptyList();
        int end = Math.min(start + pageSize, all.size());

        return all.subList(start, end);
    }

    @Override
    public List<WorldMetadata> listAllWorlds() throws StorageException {
        File[] files = containerDir.listFiles();
        if (files == null) return Collections.emptyList();

        List<WorldMetadata> worlds = new ArrayList<>();
        for (File file : files) {
            if (file.isDirectory()) {
                WorldMetadata meta = getWorld(file.getName());
                if (meta != null) {
                    worlds.add(meta);
                }
            }
        }
        return worlds;
    }

    @Override
    public void deleteWorld(@NotNull String name) throws StorageException {
        // Handled by WorldService deleting the folder.
        // We don't need to do anything special here as the metadata is inside the folder.
    }

    @Override
    public void renameWorld(@NotNull String oldName, @NotNull String newName, @NotNull String newFolderName) throws StorageException {
        // Handled by WorldService renaming the folder.
        // After rename, we should update the 'name' and 'folder_name' in the YAML.

        File worldDir = new File(containerDir, newName);
        File configFile = new File(worldDir, "blueprint.yml");
        if (!configFile.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        yaml.set("name", newName);
        yaml.set("folder_name", newFolderName);
        yaml.set("updated_at", Instant.now().toEpochMilli());

        try {
            yaml.save(configFile);
        } catch (IOException e) {
            throw new StorageException("Failed to update world metadata after rename in " + configFile.getAbsolutePath(), e);
        }
    }

    @Override
    public void saveSnapshot(@NotNull SnapshotMetadata meta) throws StorageException {
        File worldSnapRoot = new File(snapshotRootDir, meta.getWorldName());
        File snapDir = new File(worldSnapRoot, meta.getSnapshotId());
        if (!snapDir.exists() && !snapDir.mkdirs()) {
             throw new StorageException("Could not create snapshot directory: " + snapDir.getAbsolutePath());
        }

        File configFile = new File(snapDir, "snapshot.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("snapshot_id", meta.getSnapshotId());
        yaml.set("world_name", meta.getWorldName());
        yaml.set("actor_uuid", meta.getActorUuid() != null ? meta.getActorUuid().toString() : null);
        yaml.set("created_at", meta.getCreatedAt().toEpochMilli());
        yaml.set("label", meta.getLabel());

        try {
            yaml.save(configFile);
        } catch (IOException e) {
            throw new StorageException("Failed to save snapshot metadata to " + configFile.getAbsolutePath(), e);
        }
    }

    @Override
    public List<SnapshotMetadata> listSnapshots(@NotNull String worldName) throws StorageException {
        File worldSnapRoot = new File(snapshotRootDir, worldName);
        File[] files = worldSnapRoot.listFiles();
        if (files == null) return Collections.emptyList();

        List<SnapshotMetadata> snapshots = new ArrayList<>();
        for (File file : files) {
            if (file.isDirectory()) {
                SnapshotMetadata meta = getSnapshot(worldName, file.getName());
                if (meta != null) {
                    snapshots.add(meta);
                }
            }
        }
        snapshots.sort(Comparator.comparing(SnapshotMetadata::getCreatedAt).reversed());
        return snapshots;
    }

    @Override
    public @Nullable SnapshotMetadata getSnapshot(@NotNull String worldName, @NotNull String snapshotId) throws StorageException {
        File snapDir = new File(new File(snapshotRootDir, worldName), snapshotId);
        File configFile = new File(snapDir, "snapshot.yml");
        if (!configFile.exists()) return null;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        try {
            String actorUuidStr = yaml.getString("actor_uuid");
            return new SnapshotMetadata(
                    yaml.getString("snapshot_id"),
                    yaml.getString("world_name"),
                    actorUuidStr != null ? UUID.fromString(actorUuidStr) : null,
                    Instant.ofEpochMilli(yaml.getLong("created_at")),
                    yaml.getString("label")
            );
        } catch (Exception e) {
            logger.warning("Failed to load snapshot metadata from " + configFile.getAbsolutePath() + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public void deleteSnapshot(@NotNull String worldName, @NotNull String snapshotId) throws StorageException {
        // Handled by SnapshotService deleting the directory
    }

    @Override
    public void logAudit(@NotNull AuditAction action, @NotNull String worldName, @Nullable UUID actorUuid, @Nullable String detail) throws StorageException {
        // Audit logging to YAML is complex if we want a single file.
        // For now, let's just log to console or skip if not strictly required in per-world files.
        // The user didn't specify where audit logs should go in the new system.
        logger.info("[AUDIT] [" + worldName + "] " + action + " by " + (actorUuid != null ? actorUuid : "CONSOLE") + ": " + detail);
    }
}
