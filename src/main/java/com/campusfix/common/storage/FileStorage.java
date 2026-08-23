package com.campusfix.common.storage;

import java.io.InputStream;

/**
 * Where uploaded files actually live.
 *
 * <p>The rest of the application only knows this interface. Whether a photo sits
 * on a disk or in an S3 bucket is a deployment decision, not something
 * {@code AttachmentService} should have an opinion about.
 *
 * <p>Two implementations exist:
 * <ul>
 *   <li>{@link LocalFileStorage} — the default, so a fresh clone runs with no
 *       accounts and no Docker.</li>
 *   <li>{@link S3FileStorage} — active under the {@code s3} profile, talking to
 *       MinIO locally or AWS in production.</li>
 * </ul>
 *
 * <p>A {@code key} is a path-like identifier the service chooses, such as
 * {@code requests/42/a1b2c3.jpg}. It is never taken from the uploaded filename.
 */
public interface FileStorage {

    /**
     * @param size the byte count, which S3 needs up front and which lets the
     *             local implementation avoid buffering the whole file in memory
     */
    void store(String key, InputStream content, long size, String contentType);

    /** Caller closes the stream. */
    InputStream retrieve(String key);

    void delete(String key);

    /** For logs and the admin's benefit — "files are going to /var/campusfix". */
    String describe();
}
