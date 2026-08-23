package com.campusfix.attachment;

import com.campusfix.attachment.dto.AttachmentResponse;
import com.campusfix.common.exception.BusinessRuleException;
import com.campusfix.common.exception.InvalidRequestException;
import com.campusfix.common.exception.ResourceNotFoundException;
import com.campusfix.common.security.AuthenticatedUser;
import com.campusfix.common.security.CurrentUser;
import com.campusfix.common.storage.FileStorage;
import com.campusfix.common.storage.StorageException;
import com.campusfix.common.storage.StorageProperties;
import com.campusfix.request.RequestScope;
import com.campusfix.request.ServiceRequest;
import com.campusfix.request.ServiceRequestRepository;
import com.campusfix.user.User;
import com.campusfix.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class AttachmentService {

    /** Enough bytes to identify any format this application accepts. */
    private static final int MAGIC_BYTES = 12;

    private final AttachmentRepository attachmentRepository;
    private final ServiceRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final FileStorage fileStorage;
    private final StorageProperties properties;
    private final CurrentUser currentUser;
    private final Clock clock;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             ServiceRequestRepository requestRepository,
                             UserRepository userRepository,
                             FileStorage fileStorage,
                             StorageProperties properties,
                             CurrentUser currentUser,
                             Clock clock) {
        this.attachmentRepository = attachmentRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.fileStorage = fileStorage;
        this.properties = properties;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public AttachmentResponse upload(Long requestId, MultipartFile file) {
        AuthenticatedUser signedIn = currentUser.require();
        ServiceRequest request = visibleRequest(requestId, signedIn);

        if (request.getStatus().isFinal()) {
            throw new BusinessRuleException(
                    "This request is " + request.getStatus().getDisplayName().toLowerCase()
                            + ". Photos cannot be added to a finished request.");
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Choose a file to upload");
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new InvalidRequestException(
                    "That file is too large. The limit is " + properties.maxFileSizeMb() + " MB.");
        }
        if (attachmentRepository.countByRequestId(requestId) >= properties.maxFilesPerRequest()) {
            throw new BusinessRuleException(
                    "This request already has the maximum of " + properties.maxFilesPerRequest() + " photos.");
        }

        // The type is decided by the file's own bytes. Neither the browser's
        // Content-Type header nor the filename is trusted for this — both are
        // supplied by whoever is uploading.
        ImageType type = detectType(file);

        String key = "requests/%d/%s.%s".formatted(requestId, UUID.randomUUID(), type.getExtension());

        try (InputStream content = file.getInputStream()) {
            fileStorage.store(key, content, file.getSize(), type.getContentType());
        } catch (IOException e) {
            throw new StorageException("Could not read the uploaded file", e);
        }

        User uploader = userRepository.findById(signedIn.id())
                .orElseThrow(() -> new ResourceNotFoundException("User", signedIn.id()));

        Attachment attachment = attachmentRepository.save(new Attachment(
                request, uploader, safeFilename(file.getOriginalFilename(), type),
                key, type.getContentType(), file.getSize(), clock.instant()));

        return AttachmentResponse.from(attachment);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listFor(Long requestId) {
        visibleRequest(requestId, currentUser.require());
        return attachmentRepository.findForRequest(requestId).stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    /**
     * The bytes, for the download endpoint. Visibility is re-checked here rather
     * than trusted from the listing: an attachment id is a guessable number, and
     * this is the endpoint that actually hands over the data.
     */
    @Transactional(readOnly = true)
    public DownloadableFile download(Long requestId, Long attachmentId) {
        AuthenticatedUser signedIn = currentUser.require();

        Attachment attachment = attachmentRepository.findByIdWithRequest(attachmentId)
                .filter(found -> found.getRequest().getId().equals(requestId))
                .filter(found -> RequestScope.forUser(signedIn).permits(found.getRequest()))
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));

        return new DownloadableFile(
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                fileStorage.retrieve(attachment.getStorageKey()));
    }

    /**
     * Removing a photo is limited to the person who uploaded it, or an admin.
     * A technician should not be able to delete the student's evidence.
     */
    @Transactional
    public void delete(Long requestId, Long attachmentId) {
        AuthenticatedUser signedIn = currentUser.require();

        Attachment attachment = attachmentRepository.findByIdWithRequest(attachmentId)
                .filter(found -> found.getRequest().getId().equals(requestId))
                .filter(found -> RequestScope.forUser(signedIn).permits(found.getRequest()))
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));

        boolean allowed = signedIn.isAdmin()
                || attachment.getUploadedBy().getId().equals(signedIn.id());
        if (!allowed) {
            throw new BusinessRuleException("Only the person who uploaded a photo can remove it");
        }

        // The row goes first. If the file delete fails afterwards, the result is
        // an unreferenced file rather than a listing pointing at nothing —
        // wasted space is a smaller problem than a broken download.
        attachmentRepository.delete(attachment);
        fileStorage.delete(attachment.getStorageKey());
    }

    public record DownloadableFile(String filename, String contentType, long size, InputStream content) {
    }

    private ImageType detectType(MultipartFile file) {
        byte[] header = new byte[MAGIC_BYTES];
        try (InputStream stream = file.getInputStream()) {
            int read = stream.readNBytes(header, 0, MAGIC_BYTES);
            if (read < 3) {
                throw new InvalidRequestException("That file is empty or unreadable");
            }
        } catch (IOException e) {
            throw new InvalidRequestException("That file could not be read");
        }

        return ImageType.detect(header).orElseThrow(() -> new InvalidRequestException(
                "Only images are accepted (" + ImageType.acceptedFormats()
                        + "). Renaming a file does not change what it is."));
    }

    /**
     * A display name for the download, built from the original but stripped of
     * anything that could be used as a path. The stored key is a UUID and never
     * involves this value, so this is about what the browser saves it as.
     */
    private String safeFilename(String original, ImageType type) {
        if (original == null || original.isBlank()) {
            return "photo." + type.getExtension();
        }
        String cleaned = original
                .replace("\\", "/")
                .substring(original.replace("\\", "/").lastIndexOf('/') + 1)
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) {
            return "photo." + type.getExtension();
        }
        return cleaned.length() > 200 ? cleaned.substring(cleaned.length() - 200) : cleaned;
    }

    /** Reuses the one visibility rule instead of inventing a second one here. */
    private ServiceRequest visibleRequest(Long requestId, AuthenticatedUser signedIn) {
        RequestScope scope = RequestScope.forUser(signedIn);
        return requestRepository.findByIdWithDetail(requestId)
                .filter(scope::permits)
                .orElseThrow(() -> new ResourceNotFoundException("Request", requestId));
    }
}
