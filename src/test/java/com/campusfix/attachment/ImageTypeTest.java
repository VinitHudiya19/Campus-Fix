package com.campusfix.attachment;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The upload security check, tested on its own because it is the piece that
 * decides whether a file is safe to keep.
 */
class ImageTypeTest {

    @Test
    void recognisesTheFormatsWeAccept() {
        assertThat(ImageType.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
                .contains(ImageType.PNG);
        assertThat(ImageType.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0))).contains(ImageType.JPEG);
        assertThat(ImageType.detect(bytes(0x47, 0x49, 0x46, 0x38, 0x39, 0x61))).contains(ImageType.GIF);
    }

    @Test
    void aRenamedScriptIsNotAnImage() {
        // The exact attack this check exists for: a file called photo.jpg, sent
        // with Content-Type image/jpeg, whose contents are a web shell. Neither
        // the name nor the header is consulted — only the bytes.
        byte[] script = "<?php system($_GET['c']); ?>".getBytes(StandardCharsets.UTF_8);

        assertThat(ImageType.detect(script)).isEmpty();
    }

    @Test
    void riffAloneIsNotEnoughForWebp() {
        // "RIFF" also begins AVI and WAV files, so the WEBP marker at byte 8
        // has to be there too.
        byte[] wav = bytes(0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 'W', 'A', 'V', 'E');
        byte[] webp = bytes(0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P');

        assertThat(ImageType.detect(wav)).isEmpty();
        assertThat(ImageType.detect(webp)).contains(ImageType.WEBP);
    }

    @Test
    void aTruncatedOrEmptyFileIsRejected() {
        assertThat(ImageType.detect(new byte[0])).isEmpty();
        assertThat(ImageType.detect(bytes(0xFF, 0xD8))).isEmpty();   // JPEG needs three bytes
    }

    private byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
