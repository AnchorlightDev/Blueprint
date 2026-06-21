package dev.anchorlight.blueprint.model;

/**
 * Lifecycle status of a Blueprint-managed world.
 */
public enum WorldStatus {
    /** World is loaded and editable. */
    OPEN,
    /** World is unloaded; no players can enter. */
    CLOSED,
    /** World is loaded but protected against modifications. */
    LOCKED,
    /** World is retired and not visible in normal listings. */
    ARCHIVED
}
