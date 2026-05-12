package dev.anchorlight.blueprint.database;

import dev.anchorlight.blueprint.model.AuditAction;
import dev.anchorlight.blueprint.model.SnapshotMetadata;
import dev.anchorlight.blueprint.model.WorldMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Storage abstraction for Blueprint persistence operations.
 * All methods may throw {@link StorageException} on failure.
 */
public interface BlueprintStorage {

    /** Create tables if they do not already exist. */
    void initializeSchema() throws StorageException;

    /** Close underlying connection pool. */
    void close();

    // ── World operations ──────────────────────────────────────────────────

    void saveWorld(@NotNull WorldMetadata meta) throws StorageException;

    @Nullable WorldMetadata getWorld(@NotNull String name) throws StorageException;

    @Nullable WorldMetadata getWorldByFolder(@NotNull String folderName) throws StorageException;

    List<WorldMetadata> listWorlds(int page, int pageSize) throws StorageException;

    /** Returns every world record with no pagination — used for startup recovery. */
    List<WorldMetadata> listAllWorlds() throws StorageException;

    void deleteWorld(@NotNull String name) throws StorageException;

    /**
     * Atomically renames a world record and updates all snapshot references to the new name.
     * Executes in a single SQLite transaction.
     */
    void renameWorld(@NotNull String oldName, @NotNull String newName, @NotNull String newFolderName)
            throws StorageException;

    // ── Snapshot operations ───────────────────────────────────────────────

    void saveSnapshot(@NotNull SnapshotMetadata meta) throws StorageException;

    List<SnapshotMetadata> listSnapshots(@NotNull String worldName) throws StorageException;

    @Nullable SnapshotMetadata getSnapshot(@NotNull String worldName, @NotNull String snapshotId) throws StorageException;

    void deleteSnapshot(@NotNull String worldName, @NotNull String snapshotId) throws StorageException;

    // ── Audit log ─────────────────────────────────────────────────────────

    void logAudit(
            @NotNull AuditAction action,
            @NotNull String worldName,
            @Nullable UUID actorUuid,
            @Nullable String detail
    ) throws StorageException;
}
