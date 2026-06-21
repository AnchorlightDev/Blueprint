package dev.anchorlight.blueprint.model;

/**
 * Enumeration of auditable actions logged to the blueprint_audit_log table.
 */
public enum AuditAction {
    CREATE_WORLD,
    IMPORT_WORLD,
    OPEN_WORLD,
    CLOSE_WORLD,
    LOCK_WORLD,
    UNLOCK_WORLD,
    CLONE_WORLD,
    RENAME_WORLD,
    CREATE_SNAPSHOT,
    RESTORE_SNAPSHOT,
    DELETE_SNAPSHOT,
    DELETE_WORLD
}
