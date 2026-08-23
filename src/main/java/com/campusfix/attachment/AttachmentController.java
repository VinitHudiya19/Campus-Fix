package com.campusfix.attachment;

import com.campusfix.attachment.dto.AttachmentResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/requests/{requestId}/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping
    public List<AttachmentResponse> list(@PathVariable Long requestId) {
        return attachmentService.listFor(requestId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> upload(@PathVariable Long requestId,
                                                     @RequestParam("file") MultipartFile file) {
        AttachmentResponse created = attachmentService.upload(requestId, file);
        return ResponseEntity
                .created(URI.create(created.downloadUrl()))
                .body(created);
    }

    /**
     * Streams the file back.
     *
     * <p>Three headers matter more than the body:
     * <ul>
     *   <li>{@code Content-Disposition: attachment} makes the browser save the
     *       file rather than render it in the page. Even though only images get
     *       this far, rendering user-supplied content on the application's own
     *       origin is the mistake that turns an upload into stored XSS.</li>
     *   <li>{@code X-Content-Type-Options: nosniff} stops the browser
     *       second-guessing the declared type and treating the bytes as
     *       something more dangerous.</li>
     *   <li>The content type is the one detected from the file's own bytes at
     *       upload, never the one the uploader claimed.</li>
     * </ul>
     */
    @GetMapping("/{attachmentId}")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long requestId,
                                                        @PathVariable Long attachmentId) {
        AttachmentService.DownloadableFile file = attachmentService.download(requestId, attachmentId);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.size())
                .body(new InputStreamResource(file.content()));
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long requestId, @PathVariable Long attachmentId) {
        attachmentService.delete(requestId, attachmentId);
    }
}
