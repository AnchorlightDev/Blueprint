package dev.anchorlight.blueprint.service;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents concurrent destructive operations (clone, snapshot restore, delete)
 * from running on the same world simultaneously.
 *
 * <p>Operations must call {@link #tryLock(String)} before starting and
 * {@link #unlock(String)} in a finally block when done.</p>
 */
public class OperationLockService {

    private final Set<String> activeOperations = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Attempts to acquire an exclusive operation lock for the given world name.
     *
     * @return {@code true} if the lock was acquired; {@code false} if the world is already locked
     */
    public boolean tryLock(@NotNull String worldName) {
        return activeOperations.add(worldName.toLowerCase());
    }

    /**
     * Releases the operation lock for the given world name.
     * Safe to call even if the world was not locked (no-op).
     */
    public void unlock(@NotNull String worldName) {
        activeOperations.remove(worldName.toLowerCase());
    }

    /** Returns {@code true} if the world currently has an active operation lock. */
    public boolean isLocked(@NotNull String worldName) {
        return activeOperations.contains(worldName.toLowerCase());
    }
}
