package com.campusfix.attachment;

import java.util.Arrays;
import java.util.Optional;

/**
 * The image formats CampusFix accepts, identified by what the file actually
 * contains rather than by what it claims to be.
 *
 * <p>This is the important part of upload security. A browser sends a
 * {@code Content-Type} header and a filename, and <em>both come from the
 * client</em> — anyone can rename {@code shell.jsp} to {@code photo.jpg} and set
 * the header to {@code image/jpeg}. Trusting either is how a file upload becomes
 * a way to run code on the server.
 *
 * <p>Every real file format begins with a fixed byte sequence, its "magic
 * number". Those cannot be faked without the file genuinely being that format.
 */
public enum ImageType {

    PNG("image/png", "png", new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),

    /** Covers every JPEG variant; the fourth byte differs between them. */
    JPEG("image/jpeg", "jpg", new int[]{0xFF, 0xD8, 0xFF}),

    GIF("image/gif", "gif", new int[]{0x47, 0x49, 0x46, 0x38}),

    /** RIFF container: bytes 0–3 are "RIFF" and 8–11 are "WEBP". */
    WEBP("image/webp", "webp", new int[]{0x52, 0x49, 0x46, 0x46});

    private final String contentType;
    private final String extension;
    private final int[] magic;

    ImageType(String contentType, String extension, int[] magic) {
        this.contentType = contentType;
        this.extension = extension;
        this.magic = magic;
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return extension;
    }

    /**
     * @param header the first bytes of the uploaded file
     * @return the format the bytes really are, or empty if it is not an image
     *         this application accepts
     */
    public static Optional<ImageType> detect(byte[] header) {
        return Arrays.stream(values())
                .filter(type -> type.matches(header))
                .findFirst();
    }

    private boolean matches(byte[] header) {
        if (header.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((header[i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        // RIFF alone is also AVI and WAV, so WEBP needs its second marker.
        if (this == WEBP) {
            return header.length >= 12
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
        }
        return true;
    }

    public static String acceptedFormats() {
        return "PNG, JPEG, GIF or WebP";
    }
}
