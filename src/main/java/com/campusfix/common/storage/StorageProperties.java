package com.campusfix.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for both storage implementations.
 *
 * @param location  where local files go. Deliberately outside the application's
 *                  own directory: a folder the web server serves is exactly
 *                  where an uploaded file must not land.
 * @param maxFileSizeBytes the per-file cap the service enforces. Tomcat has its
 *                  own limit in application.properties; this one produces a
 *                  friendly message instead of a container-level rejection.
 * @param maxFilesPerRequest stops one request accumulating a hundred photos.
 * @param bucket    S3 bucket name, used only under the {@code s3} profile.
 * @param endpoint  S3 endpoint. Empty means real AWS; set it to MinIO's address
 *                  to run locally.
 * @param region    S3 region. MinIO ignores it but the SDK insists on one.
 */
@ConfigurationProperties(prefix = "campusfix.storage")
public record StorageProperties(
        String location,
        long maxFileSizeBytes,
        int maxFilesPerRequest,
        String bucket,
        String endpoint,
        String region) {

    public StorageProperties {
        if (maxFileSizeBytes <= 0) {
            throw new IllegalStateException("campusfix.storage.max-file-size-bytes must be positive");
        }
        if (maxFilesPerRequest <= 0) {
            throw new IllegalStateException("campusfix.storage.max-files-per-request must be positive");
        }
    }

    public long maxFileSizeMb() {
        return maxFileSizeBytes / (1024 * 1024);
    }
}
