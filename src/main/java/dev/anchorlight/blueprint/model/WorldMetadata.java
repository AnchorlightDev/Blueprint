package dev.anchorlight.blueprint.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent metadata record for a Blueprint-managed world.
 */
public class WorldMetadata {

    /** Logical display name used in commands (e.g. "myworld"). */
    private final String name;

    /** Actual Bukkit folder name (e.g. "blueprint_myworld"). */
    private final String folderName;

    /** UUID of the player who created this world; null for console-created worlds. */
    @Nullable
    private final UUID ownerUuid;

    private WorldStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    @Nullable private Instant lastOpenedAt;
    @Nullable private Instant lastClosedAt;

    public WorldMetadata(
            @NotNull String name,
            @NotNull String folderName,
            @Nullable UUID ownerUuid,
            @NotNull WorldStatus status,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt,
            @Nullable Instant lastOpenedAt,
            @Nullable Instant lastClosedAt) {
        this.name = name;
        this.folderName = folderName;
        this.ownerUuid = ownerUuid;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastOpenedAt = lastOpenedAt;
        this.lastClosedAt = lastClosedAt;
    }

    // -- Getters --

    public @NotNull String getName() { return name; }
    public @NotNull String getFolderName() { return folderName; }
    public @Nullable UUID getOwnerUuid() { return ownerUuid; }
    public @NotNull WorldStatus getStatus() { return status; }
    public @NotNull Instant getCreatedAt() { return createdAt; }
    public @NotNull Instant getUpdatedAt() { return updatedAt; }
    public @Nullable Instant getLastOpenedAt() { return lastOpenedAt; }
    public @Nullable Instant getLastClosedAt() { return lastClosedAt; }

    // -- Setters for mutable fields --

    public void setStatus(@NotNull WorldStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void markOpened() {
        this.lastOpenedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markClosed() {
        this.lastClosedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @Override
    public String toString() {
        return "WorldMetadata{name='" + name + "', status=" + status + '}';
    }
}
