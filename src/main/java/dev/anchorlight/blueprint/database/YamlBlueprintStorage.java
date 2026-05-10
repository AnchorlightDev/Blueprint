package dev.anchorlight.blueprint.database;

import dev.anchorlight.blueprint.model.AuditAction;
import dev.anchorlight.blueprint.model.SnapshotMetadata;
import dev.anchorlight.blueprint.model.WorldMetadata;
import dev.anchorlight.blueprint.model.WorldStatus;
import dev.anchorlight.blueprint.util.yaml.YamlBlueprintConfig;
import dev.anchorlight.blueprint.util.yaml.YamlBlueprintConfigFile;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.time.Instant;
import java.util.logging.Logger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * YAML implementation of {@link BlueprintStorage}.
 * Each world stores its own metadata in a blueprint.yml file.
 * Audit logs are stored in the plugin data folder.
 */
public class YamlBlueprintStorage implements BlueprintStorage {

    private final File dataFolder;
    private final Logger logger;
    private final String folderPrefix;

    public YamlBlueprintStorage(@NotNull File dataFolder, @NotNull Logger logger, @NotNull String folderPrefix) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.folderPrefix = folderPrefix;
    }

    @Override
    public void initializeSchema() throws StorageException {
        // No schema to initialize for YAML
    }

    @Override
    public void close() {
        // Nothing to close
    }

    @Override
    public void saveWorld(@NotNull WorldMetadata meta) throws StorageException {
        File worldFolder = new File(Bukkit.getWorldContainer(), meta.getFolderName());
        if (!worldFolder.exists()) {
            return;
        }

        File yamlFile = new File(worldFolder, "blueprint.yml");
        YamlBlueprintConfigFile config = new YamlBlueprintConfigFile(yamlFile);

        config.set("name", meta.getName());
        config.set("folder_name", meta.getFolderName());
        config.set("owner_uuid", meta.getOwnerUuid() != null ? meta.getOwnerUuid().toString() : null);
        config.set("status", meta.getStatus().name());
        config.set("created_at", meta.getCreatedAt().toEpochMilli());
        config.set("updated_at", meta.getUpdatedAt().toEpochMilli());
        config.set("last_opened_at", meta.getLastOpenedAt() != null ? meta.getLastOpenedAt().toEpochMilli() : null);
        config.set("last_closed_at", meta.getLastClosedAt() != null ? meta.getLastClosedAt().toEpochMilli() : null);

        config.save();
    }

    @Override
    public @Nullable WorldMetadata getWorld(@NotNull String name) throws StorageException {
        for (WorldMetadata meta : listAllWorlds()) {
            if (meta.getName().equalsIgnoreCase(name)) {
                return meta;
            }
        }
        return null;
    }

    @Override
    public @Nullable WorldMetadata getWorldByFolder(@NotNull String folderName) throws StorageException {
        File worldFolder = new File(Bukkit.getWorldContainer(), folderName);
        File yamlFile = new File(worldFolder, "blueprint.yml");
        if (!yamlFile.exists()) return null;

        try {
            YamlBlueprintConfigFile config = new YamlBlueprintConfigFile(yamlFile);
            return mapWorld(config);
        } catch (Exception e) {
            throw new StorageException("Failed to load world meta from " + folderName, e);
        }
    }

    @Override
    public List<WorldMetadata> listWorlds(int page, int pageSize) throws StorageException {
        List<WorldMetadata> all = listAllWorlds();
        int fromIndex = (page - 1) * pageSize;
        if (fromIndex >= all.size()) return Collections.emptyList();
        int toIndex = Math.min(fromIndex + pageSize, all.size());
        return all.subList(fromIndex, toIndex);
    }

    @Override
    public List<WorldMetadata> listAllWorlds() throws StorageException {
        File container = Bukkit.getWorldContainer();
        File[] folders = container.listFiles(f -> f.isDirectory() && f.getName().startsWith(folderPrefix));
        if (folders == null) return Collections.emptyList();

        List<WorldMetadata> worlds = new ArrayList<>();
        for (File folder : folders) {
            WorldMetadata meta = getWorldByFolder(folder.getName());
            if (meta != null) {
                worlds.add(meta);
            }
        }
        worlds.sort(Comparator.comparing(WorldMetadata::getCreatedAt).reversed());
        return worlds;
    }

    @Override
    public void deleteWorld(@NotNull String name) throws StorageException {
    }

    @Override
    public void renameWorld(@NotNull String oldName, @NotNull String newName, @NotNull String newFolderName) throws StorageException {
        WorldMetadata meta = getWorld(newName);
        if (meta != null) {
            saveWorld(meta);
        }
    }

    @Override
    public void saveSnapshot(@NotNull SnapshotMetadata meta) throws StorageException {
        WorldMetadata worldMeta = getWorld(meta.getWorldName());
        if (worldMeta == null) return;

        File worldFolder = new File(Bukkit.getWorldContainer(), worldMeta.getFolderName());
        File yamlFile = new File(worldFolder, "blueprint.yml");
        YamlBlueprintConfigFile config = new YamlBlueprintConfigFile(yamlFile);

        List<Map<String, Object>> snapshots = getSnapshotData(config);

        snapshots.removeIf(s -> meta.getSnapshotId().equals(s.get("id")));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", meta.getSnapshotId());
        data.put("actor_uuid", meta.getActorUuid() != null ? meta.getActorUuid().toString() : null);
        data.put("created_at", meta.getCreatedAt().toEpochMilli());
        data.put("label", meta.getLabel());

        snapshots.add(data);
        config.set("snapshots", snapshots);
        config.save();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getSnapshotData(YamlBlueprintConfigFile config) {
        Object obj = config.getData().get("snapshots");
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof YamlBlueprintConfig) {
                    result.add(((YamlBlueprintConfig) item).getData());
                } else if (item instanceof Map) {
                    result.add((Map<String, Object>) item);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    @Override
    public List<SnapshotMetadata> listSnapshots(@NotNull String worldName) throws StorageException {
        WorldMetadata worldMeta = getWorld(worldName);
        if (worldMeta == null) return Collections.emptyList();

        File worldFolder = new File(Bukkit.getWorldContainer(), worldMeta.getFolderName());
        File yamlFile = new File(worldFolder, "blueprint.yml");
        if (!yamlFile.exists()) return Collections.emptyList();

        YamlBlueprintConfigFile config = new YamlBlueprintConfigFile(yamlFile);
        List<Map<String, Object>> snapshotsData = getSnapshotData(config);

        return snapshotsData.stream()
                .map(data -> mapSnapshot(data, worldName))
                .sorted(Comparator.comparing(SnapshotMetadata::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public @Nullable SnapshotMetadata getSnapshot(@NotNull String worldName, @NotNull String snapshotId) throws StorageException {
        return listSnapshots(worldName).stream()
                .filter(s -> s.getSnapshotId().equals(snapshotId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void deleteSnapshot(@NotNull String worldName, @NotNull String snapshotId) throws StorageException {
        WorldMetadata worldMeta = getWorld(worldName);
        if (worldMeta == null) return;

        File worldFolder = new File(Bukkit.getWorldContainer(), worldMeta.getFolderName());
        File yamlFile = new File(worldFolder, "blueprint.yml");
        if (!yamlFile.exists()) return;

        YamlBlueprintConfigFile config = new YamlBlueprintConfigFile(yamlFile);
        List<Map<String, Object>> snapshots = getSnapshotData(config);
        snapshots.removeIf(s -> snapshotId.equals(s.get("id")));
        config.set("snapshots", snapshots);
        config.save();
    }

    @Override
    public void logAudit(@NotNull AuditAction action, @NotNull String worldName, @Nullable UUID actorUuid, @Nullable String detail) throws StorageException {
        File auditFile = new File(dataFolder, "audit.yml");
        YamlBlueprintConfigFile config = new YamlBlueprintConfigFile(auditFile);

        List<Map<String, Object>> logs = getLogs(config);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("action", action.name());
        entry.put("world", worldName);
        entry.put("actor", actorUuid != null ? actorUuid.toString() : null);
        entry.put("detail", detail);
        entry.put("occurred_at", Instant.now().toEpochMilli());

        logs.add(entry);

        if (logs.size() > 1000) {
            logs = logs.subList(logs.size() - 1000, logs.size());
        }

        config.set("logs", logs);
        config.save();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getLogs(YamlBlueprintConfigFile config) {
        Object obj = config.getData().get("logs");
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof YamlBlueprintConfig) {
                    result.add(((YamlBlueprintConfig) item).getData());
                } else if (item instanceof Map) {
                    result.add((Map<String, Object>) item);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    private WorldMetadata mapWorld(YamlBlueprintConfigFile config) {
        String name = config.getString("name");
        String folderName = config.getString("folder_name");
        String ownerStr = config.getString("owner_uuid");
        String statusStr = config.getString("status", "CLOSED");
        long createdAt = config.getLong("created_at");
        long updatedAt = config.getLong("updated_at");

        Instant lastOpened = config.contains("last_opened_at") && config.get("last_opened_at") != null
                ? Instant.ofEpochMilli(config.getLong("last_opened_at")) : null;
        Instant lastClosed = config.contains("last_closed_at") && config.get("last_closed_at") != null
                ? Instant.ofEpochMilli(config.getLong("last_closed_at")) : null;

        return new WorldMetadata(
                name,
                folderName,
                ownerStr != null && !ownerStr.isEmpty() ? UUID.fromString(ownerStr) : null,
                WorldStatus.valueOf(statusStr),
                Instant.ofEpochMilli(createdAt),
                Instant.ofEpochMilli(updatedAt),
                lastOpened,
                lastClosed
        );
    }

    private SnapshotMetadata mapSnapshot(Map<String, Object> data, String worldName) {
        String id = (String) data.get("id");
        String actorStr = (String) data.get("actor_uuid");
        long createdAt = ((Number) data.get("created_at")).longValue();
        String label = (String) data.get("label");

        return new SnapshotMetadata(
                id,
                worldName,
                actorStr != null && !actorStr.isEmpty() ? UUID.fromString(actorStr) : null,
                Instant.ofEpochMilli(createdAt),
                label
        );
    }
}
