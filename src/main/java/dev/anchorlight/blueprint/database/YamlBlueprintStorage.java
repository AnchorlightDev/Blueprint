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
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * YAML implementation of {@link BlueprintStorage}.
 *
 * <p>Each world stores its metadata in {@code blueprint/<name>/blueprint.yml}.
 * With {@code settings.world-container: blueprint} in bukkit.yml,
 * {@link Bukkit#getWorldContainer()} returns the {@code blueprint/} directory, so
 * world folders are discovered directly as immediate children of that container.</p>
 *
 * <p>Audit logs are stored in {@code plugins/Blueprint/audit.yml}.</p>
 */
public class YamlBlueprintStorage implements BlueprintStorage {

    private final File dataFolder;
    private final Logger logger;

    public YamlBlueprintStorage(@NotNull File dataFolder, @NotNull Logger logger) {
        this.dataFolder = dataFolder;
        this.logger     = logger;
    }

    @Override
    public void initializeSchema() throws StorageException {
        // No schema to initialize for YAML
    }

    @Override
    public void close() {}

    // ── World CRUD ─────────────────────────────────────────────────────────

    @Override
    public void saveWorld(@NotNull WorldMetadata meta) throws StorageException {
        // metadata lives at: blueprint/<folderName>/blueprint.yml
        File worldFolder = worldFolder(meta.getFolderName());
        if (!worldFolder.exists() && !worldFolder.mkdirs()) {
            throw new StorageException("Could not create world folder: " + worldFolder);
        }

        File yamlFile = new File(worldFolder, "blueprint.yml");
        YamlBlueprintConfigFile config = new YamlBlueprintConfigFile(yamlFile);
        // preserve existing snapshots list when overwriting
        List<Map<String, Object>> snapshots = getSnapshotData(config);

        config.set("name",           meta.getName());
        config.set("folder_name",    meta.getFolderName());
        config.set("owner_uuid",     meta.getOwnerUuid() != null ? meta.getOwnerUuid().toString() : null);
        config.set("status",         meta.getStatus().name());
        config.set("created_at",     meta.getCreatedAt().toEpochMilli());
        config.set("updated_at",     meta.getUpdatedAt().toEpochMilli());
        config.set("last_opened_at", meta.getLastOpenedAt() != null ? meta.getLastOpenedAt().toEpochMilli() : null);
        config.set("last_closed_at", meta.getLastClosedAt() != null ? meta.getLastClosedAt().toEpochMilli() : null);
        config.set("restricted",     meta.isRestricted());
        config.set("snapshots",      snapshots);
        config.save();
    }

    @Override
    public @Nullable WorldMetadata getWorld(@NotNull String name) throws StorageException {
        for (WorldMetadata meta : listAllWorlds()) {
            if (meta.getName().equalsIgnoreCase(name)) return meta;
        }
        return null;
    }

    @Override
    public @Nullable WorldMetadata getWorldByFolder(@NotNull String folderName) throws StorageException {
        File yamlFile = new File(worldFolder(folderName), "blueprint.yml");
        if (!yamlFile.exists()) return null;
        try {
            return mapWorld(new YamlBlueprintConfigFile(yamlFile));
        } catch (Exception e) {
            throw new StorageException("Failed to load world from folder '" + folderName + "'", e);
        }
    }

    @Override
    public List<WorldMetadata> listWorlds(int page, int pageSize) throws StorageException {
        List<WorldMetadata> all = listAllWorlds();
        int from = (page - 1) * pageSize;
        if (from >= all.size()) return Collections.emptyList();
        return all.subList(from, Math.min(from + pageSize, all.size()));
    }

    @Override
    public List<WorldMetadata> listAllWorlds() throws StorageException {
        // Scan the world container directory (= blueprint/) directly.
        // Each immediate subdirectory with a blueprint.yml is a managed world.
        File containerDir = Bukkit.getWorldContainer();
        File[] folders = containerDir.listFiles(File::isDirectory);
        if (folders == null) return Collections.emptyList();

        List<WorldMetadata> worlds = new ArrayList<>();
        for (File folder : folders) {
            File yamlFile = new File(folder, "blueprint.yml");
            if (!yamlFile.exists()) continue;
            try {
                WorldMetadata meta = mapWorld(new YamlBlueprintConfigFile(yamlFile));
                if (meta != null) worlds.add(meta);
            } catch (Exception e) {
                logger.warning("[Blueprint] Skipping corrupt blueprint.yml in " + folder.getName() + ": " + e.getMessage());
            }
        }
        worlds.sort(Comparator.comparing(WorldMetadata::getCreatedAt).reversed());
        return worlds;
    }

    @Override
    public void deleteWorld(@NotNull String name) throws StorageException {
        WorldMetadata meta = getWorld(name);
        if (meta == null) return;
        // Remove the blueprint.yml stub folder that Blueprint created.
        // (The actual world data at the dimension path was already deleted by WorldService.)
        File worldFolder = worldFolder(meta.getFolderName());
        File yamlFile    = new File(worldFolder, "blueprint.yml");
        yamlFile.delete();
        String[] remaining = worldFolder.list();
        if (remaining != null && remaining.length == 0) {
            worldFolder.delete();
        }
    }

    @Override
    public void renameWorld(@NotNull String oldName, @NotNull String newName, @NotNull String newFolderName)
            throws StorageException {
        WorldMetadata old = getWorld(oldName);
        if (old == null) return;

        WorldMetadata newMeta = new WorldMetadata(
                newName, newFolderName, old.getOwnerUuid(),
                old.getStatus(), old.getCreatedAt(), Instant.now(),
                old.getLastOpenedAt(), old.getLastClosedAt(), old.isRestricted());
        saveWorld(newMeta);

        // Copy snapshot records to the new world file
        for (SnapshotMetadata snap : listSnapshots(oldName)) {
            saveSnapshot(new SnapshotMetadata(
                    snap.getSnapshotId(), newName, snap.getActorUuid(),
                    snap.getCreatedAt(), snap.getLabel()));
        }

        // Remove old blueprint.yml
        File oldFolder = worldFolder(old.getFolderName());
        new File(oldFolder, "blueprint.yml").delete();
        String[] remaining = oldFolder.list();
        if (remaining != null && remaining.length == 0) oldFolder.delete();
    }

    // ── Snapshots ──────────────────────────────────────────────────────────

    @Override
    public void saveSnapshot(@NotNull SnapshotMetadata meta) throws StorageException {
        WorldMetadata worldMeta = getWorld(meta.getWorldName());
        if (worldMeta == null) return;

        File yamlFile = new File(worldFolder(worldMeta.getFolderName()), "blueprint.yml");
        YamlBlueprintConfigFile config = new YamlBlueprintConfigFile(yamlFile);
        List<Map<String, Object>> snapshots = getSnapshotData(config);
        snapshots.removeIf(s -> meta.getSnapshotId().equals(s.get("id")));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id",         meta.getSnapshotId());
        data.put("actor_uuid", meta.getActorUuid() != null ? meta.getActorUuid().toString() : null);
        data.put("created_at", meta.getCreatedAt().toEpochMilli());
        data.put("label",      meta.getLabel());

        snapshots.add(data);
        config.set("snapshots", snapshots);
        config.save();
    }

    @Override
    public List<SnapshotMetadata> listSnapshots(@NotNull String worldName) throws StorageException {
        WorldMetadata worldMeta = getWorld(worldName);
        if (worldMeta == null) return Collections.emptyList();

        File yamlFile = new File(worldFolder(worldMeta.getFolderName()), "blueprint.yml");
        if (!yamlFile.exists()) return Collections.emptyList();

        return getSnapshotData(new YamlBlueprintConfigFile(yamlFile)).stream()
                .map(d -> mapSnapshot(d, worldName))
                .sorted(Comparator.comparing(SnapshotMetadata::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public @Nullable SnapshotMetadata getSnapshot(@NotNull String worldName, @NotNull String snapshotId)
            throws StorageException {
        return listSnapshots(worldName).stream()
                .filter(s -> s.getSnapshotId().equals(snapshotId))
                .findFirst().orElse(null);
    }

    @Override
    public void deleteSnapshot(@NotNull String worldName, @NotNull String snapshotId) throws StorageException {
        WorldMetadata worldMeta = getWorld(worldName);
        if (worldMeta == null) return;

        File yamlFile = new File(worldFolder(worldMeta.getFolderName()), "blueprint.yml");
        if (!yamlFile.exists()) return;

        YamlBlueprintConfigFile config = new YamlBlueprintConfigFile(yamlFile);
        List<Map<String, Object>> snapshots = getSnapshotData(config);
        snapshots.removeIf(s -> snapshotId.equals(s.get("id")));
        config.set("snapshots", snapshots);
        config.save();
    }

    // ── Audit ──────────────────────────────────────────────────────────────

    @Override
    public void logAudit(@NotNull AuditAction action, @NotNull String worldName,
                         @Nullable UUID actorUuid, @Nullable String detail) throws StorageException {
        try {
            File auditFile = new File(dataFolder, "audit.yml");
            YamlBlueprintConfigFile config = new YamlBlueprintConfigFile(auditFile);
            List<Map<String, Object>> logs = getLogs(config);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("action",      action.name());
            entry.put("world",       worldName);
            entry.put("actor",       actorUuid != null ? actorUuid.toString() : null);
            entry.put("detail",      detail);
            entry.put("occurred_at", Instant.now().toEpochMilli());
            logs.add(entry);

            if (logs.size() > 1000) logs = logs.subList(logs.size() - 1000, logs.size());
            config.set("logs", logs);
            config.save();
        } catch (Exception e) {
            logger.warning("[Blueprint] Failed to write audit log to " + dataFolder.getAbsolutePath()
                    + "/audit.yml: " + e.getMessage());
            throw new StorageException("Audit log write failed", e);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Returns the world's metadata folder: blueprint/&lt;folderName&gt;/ */
    private File worldFolder(@NotNull String folderName) {
        return new File(Bukkit.getWorldContainer(), folderName);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getSnapshotData(YamlBlueprintConfigFile config) {
        Object obj = config.getData().get("snapshots");
        if (obj instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof YamlBlueprintConfig c) result.add(c.getData());
                else if (item instanceof Map<?,?> m)        result.add((Map<String, Object>) m);
            }
            return result;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getLogs(YamlBlueprintConfigFile config) {
        Object obj = config.getData().get("logs");
        if (obj instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof YamlBlueprintConfig c) result.add(c.getData());
                else if (item instanceof Map<?,?> m)        result.add((Map<String, Object>) m);
            }
            return result;
        }
        return new ArrayList<>();
    }

    private @Nullable WorldMetadata mapWorld(YamlBlueprintConfigFile config) {
        String name = config.getString("name");
        if (name == null || name.isEmpty()) return null;
        String folderName = config.getString("folder_name");
        if (folderName == null) folderName = name;

        String ownerStr  = config.getString("owner_uuid");
        String statusStr = config.getString("status", "CLOSED");
        long createdAt   = config.getLong("created_at");
        long updatedAt   = config.getLong("updated_at");

        Instant lastOpened = config.contains("last_opened_at") && config.get("last_opened_at") != null
                ? Instant.ofEpochMilli(config.getLong("last_opened_at")) : null;
        Instant lastClosed = config.contains("last_closed_at") && config.get("last_closed_at") != null
                ? Instant.ofEpochMilli(config.getLong("last_closed_at")) : null;

        boolean restricted = config.getBoolean("restricted", false);

        return new WorldMetadata(
                name, folderName,
                ownerStr != null && !ownerStr.isEmpty() ? UUID.fromString(ownerStr) : null,
                WorldStatus.valueOf(statusStr),
                Instant.ofEpochMilli(createdAt),
                Instant.ofEpochMilli(updatedAt),
                lastOpened, lastClosed, restricted);
    }

    private SnapshotMetadata mapSnapshot(Map<String, Object> data, String worldName) {
        String id       = (String) data.get("id");
        String actorStr = (String) data.get("actor_uuid");
        long createdAt  = ((Number) data.get("created_at")).longValue();
        String label    = (String) data.get("label");

        return new SnapshotMetadata(
                id, worldName,
                actorStr != null && !actorStr.isEmpty() ? UUID.fromString(actorStr) : null,
                Instant.ofEpochMilli(createdAt), label);
    }
}
