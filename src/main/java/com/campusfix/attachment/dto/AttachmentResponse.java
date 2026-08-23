package com.campusfix.attachment.dto;

import com.campusfix.attachment.Attachment;

import java.time.Instant;

/**
 * There is no {@code storageKey} field. Where the file physically sits is the
 * server's business — exposing it would tell a client how the store is laid out
 * and invite them to guess at other keys.
 */
public record AttachmentResponse(
        Long id,
        String filename,
        String contentType,
        long fileSize,
        String readableSize,
        String uploadedByName,
        Instant createdAt,
        String downloadUrl) {

    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                readable(attachment.getFileSize()),
                attachment.getUploadedBy().getFullName(),
                attachment.getCreatedAt(),
                "/api/requests/" + attachment.getRequest().getId()
                        + "/attachments/" + attachment.getId());
    }

    private static String readable(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return Math.round(bytes / 1024.0) + " KB";
        }
        return Math.round(bytes / (1024.0 * 1024.0) * 10) / 10.0 + " MB";
    }
}
