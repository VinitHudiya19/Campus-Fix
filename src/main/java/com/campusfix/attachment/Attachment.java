package com.campusfix.attachment;

import com.campusfix.request.ServiceRequest;
import com.campusfix.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A photo attached to a request. The file itself lives in
 * {@link com.campusfix.common.storage.FileStorage}; this row is the record of it.
 *
 * <p>Note that the bytes are deliberately <em>not</em> in the database. A
 * {@code LONGBLOB} column would be transactional and would ride along with
 * backups, which sounds attractive until the database is twenty gigabytes of
 * photographs, every restore drags them with it, and a careless {@code select *}
 * pulls megabytes into memory. Files belong in a file store.
 */
@Entity
@Table(name = "attachments", indexes = {
        @Index(name = "idx_attachment_request", columnList = "request_id")
})
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ServiceRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    /**
     * What the file was called on the uploader's machine. Kept only so the
     * download can be given a familiar name — it is never used to build a path.
     */
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    /** Where the file store can find it. Generated here, never sent by a client. */
    @Column(name = "storage_key", nullable = false, unique = true, length = 255)
    private String storageKey;

    /**
     * The type detected from the file's own bytes, not the header the browser
     * claimed. This is what gets sent back on download.
     */
    @Column(name = "content_type", nullable = false, length = 60)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Attachment() {
        // required by JPA
    }

    public Attachment(ServiceRequest request, User uploadedBy, String originalFilename,
                      String storageKey, String contentType, long fileSize, Instant createdAt) {
        this.request = request;
        this.uploadedBy = uploadedBy;
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public ServiceRequest getRequest() {
        return request;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
