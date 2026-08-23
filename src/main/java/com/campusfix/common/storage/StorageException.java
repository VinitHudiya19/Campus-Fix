package com.campusfix.common.storage;

/**
 * A file could not be written or read.
 *
 * <p>This is an infrastructure failure — a full disk, an unreachable bucket —
 * not something the user did wrong, so it deliberately has no HTTP status
 * mapping and falls through to the 500 handler, which logs the real cause and
 * tells the user only that something went wrong.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
