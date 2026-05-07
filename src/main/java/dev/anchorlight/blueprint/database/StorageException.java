package dev.anchorlight.blueprint.database;

/**
 * Unchecked wrapper around storage-layer failures.
 * Commands catch this and send a user-facing error message.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
