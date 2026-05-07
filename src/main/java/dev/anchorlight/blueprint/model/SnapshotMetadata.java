package dev.anchorlight.blueprint.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent metadata for a single world snapshot.
 */
public class SnapshotMetadata {

    /** Snapshot ID, e.g. "snap-2026-05-07-153022". */
    private final String snapshotId;

    /** Logical name of the world this snapshot belongs to. */
    private final String worldName;

    /** Actor who triggered the snapshot; null for automated snapshots. */
    @Nullable
    private final UUID actorUuid;

    private final Instant createdAt;

    /** Optional human-readable label. */
    @Nullable
    private final String label;

    public SnapshotMetadata(
            @NotNull String snapshotId,
            @NotNull String worldName,
            @Nullable UUID actorUuid,
            @NotNull Instant createdAt,
            @Nullable String label) {
        this.snapshotId = snapshotId;
        this.worldName = worldName;
        this.actorUuid = actorUuid;
        this.createdAt = createdAt;
        this.label = label;
    }

    public @NotNull String getSnapshotId() { return snapshotId; }
    public @NotNull String getWorldName() { return worldName; }
    public @Nullable UUID getActorUuid() { return actorUuid; }
    public @NotNull Instant getCreatedAt() { return createdAt; }
    public @Nullable String getLabel() { return label; }

    @Override
    public String toString() {
        return "SnapshotMetadata{id='" + snapshotId + "', world='" + worldName + "'}";
    }
}
