package com.campusfix.common.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.net.URI;

/**
 * Object storage, through the S3 API.
 *
 * <p>Active only under the {@code s3} profile. The same class talks to MinIO in
 * Docker and to real AWS — that is the point of the S3 API being a de facto
 * standard, and it means the local and production setups differ by
 * configuration rather than by code.
 *
 * <p>This is what solves the two problems local disk has: the files live outside
 * the application, so a container can be replaced without losing them, and every
 * instance sees the same objects.
 */
@Component("s3FileStorage")
@Profile("s3")
public class S3FileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorage.class);

    private final S3Client client;
    private final String bucket;
    private final String description;

    public S3FileStorage(StorageProperties properties) {
        this.bucket = properties.bucket();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("campusfix.storage.bucket is required when the s3 profile is active");
        }

        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.create());

        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            // MinIO serves one host for every bucket, so the SDK's default of
            // putting the bucket in the hostname (bucket.s3.amazonaws.com) has
            // to be turned off. Real AWS ignores both of these settings.
            builder.endpointOverride(URI.create(properties.endpoint()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }

        this.client = builder.build();
        this.description = "S3 bucket '" + bucket + "'"
                + (properties.endpoint() == null || properties.endpoint().isBlank()
                    ? " on AWS" : " at " + properties.endpoint());
        log.info("Attachments are stored in {}", description);
    }

    @Override
    public void store(String key, InputStream content, long size, String contentType) {
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    // The length is passed explicitly so the SDK streams the
                    // body instead of reading it all into memory first.
                    RequestBody.fromInputStream(content, size));
        } catch (S3Exception e) {
            throw new StorageException("Could not upload the file to " + description, e);
        }
    }

    @Override
    public InputStream retrieve(String key) {
        try {
            return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException e) {
            throw new StorageException("The stored file is missing: " + key, e);
        } catch (S3Exception e) {
            throw new StorageException("Could not read the file from " + description, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception e) {
            throw new StorageException("Could not delete the file from " + description, e);
        }
    }

    @Override
    public String describe() {
        return description;
    }
}
