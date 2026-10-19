package com.example.dinamika_back.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Разбор картинок плана цеха: сигнатуры форматов и размеры из заголовков PNG, JPEG и WebP. */
class StationPlanImagesTest {

    @Test
    void readsPngSizeWithImageIo() throws IOException {
        byte[] png = encode(1200, 700, "png");
        assertThat(StationPlanImages.detect(png)).isEqualTo(StationPlanImages.Format.PNG);
        StationPlanImages.Info info = StationPlanImages.inspect(png);
        assertThat(info.format()).isEqualTo(StationPlanImages.Format.PNG);
        assertThat(info.width()).isEqualTo(1200);
        assertThat(info.height()).isEqualTo(700);
    }

    @Test
    void readsJpegSizeWithImageIo() throws IOException {
        byte[] jpeg = encode(640, 480, "jpg");
        assertThat(StationPlanImages.detect(jpeg)).isEqualTo(StationPlanImages.Format.JPEG);
        StationPlanImages.Info info = StationPlanImages.inspect(jpeg);
        assertThat(info.width()).isEqualTo(640);
        assertThat(info.height()).isEqualTo(480);
        assertThat(info.format().mime()).isEqualTo("image/jpeg");
    }

    @Test
    void readsWebpExtendedHeader() {
        // VP8X: флаги, 3 резервных байта, затем 24-битные «ширина − 1» и «высота − 1» холста
        byte[] payload = new byte[10];
        payload[0] = 0x10;
        putLe24(payload, 4, 1920 - 1);
        putLe24(payload, 7, 1080 - 1);
        byte[] webp = webp("VP8X", payload);
        assertThat(StationPlanImages.detect(webp)).isEqualTo(StationPlanImages.Format.WEBP);
        assertThat(StationPlanImages.webpSize(webp)).containsExactly(1920, 1080);
    }

    @Test
    void readsWebpLosslessHeader() {
        // VP8L: байт 0x2F, затем 14 бит «ширина − 1», 14 бит «высота − 1», бит прозрачности и версия
        long bits = (800 - 1) | ((long) (600 - 1) << 14) | (1L << 28);
        byte[] payload = {0x2F, (byte) bits, (byte) (bits >> 8), (byte) (bits >> 16), (byte) (bits >> 24), 0, 0, 0, 0, 0};
        assertThat(StationPlanImages.webpSize(webp("VP8L", payload))).containsExactly(800, 600);
    }

    @Test
    void readsWebpLossyHeaderIgnoringScaleBits() {
        // VP8: заголовок ключевого кадра, код 9D 01 2A, 14-битные размеры (старшие 2 бита — масштаб)
        int width = 1024 | (1 << 14);
        int height = 768 | (2 << 14);
        byte[] payload = {0x50, 0x2A, 0x00, (byte) 0x9D, 0x01, 0x2A,
                (byte) width, (byte) (width >> 8), (byte) height, (byte) (height >> 8)};
        assertThat(StationPlanImages.webpSize(webp("VP8 ", payload))).containsExactly(1024, 768);
    }

    @Test
    void rejectsWebpWithBrokenFrame() {
        byte[] payload = {0x50, 0x2A, 0x00, 0x11, 0x22, 0x33, 0, 4, 0, 3};
        assertThat(StationPlanImages.webpSize(webp("VP8 ", payload))).isNull();
        assertThatThrownBy(() -> StationPlanImages.inspect(webp("VP8 ", payload)))
                .isInstanceOf(StationPlanException.class)
                .hasMessageContaining("размеры");
    }

    @Test
    void rejectsOtherFormatsBySignature() {
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0};
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8);
        assertThat(StationPlanImages.detect(gif)).isNull();
        assertThat(StationPlanImages.detect(svg)).isNull();
        assertThatThrownBy(() -> StationPlanImages.inspect(svg))
                .isInstanceOf(StationPlanException.class)
                .hasMessageContaining("PNG, JPEG и WebP");
    }

    @Test
    void rejectsTooLargeSide() {
        // Холст 8193×100 — ширина больше лимита 8192 px
        byte[] payload = new byte[10];
        putLe24(payload, 4, StationPlanImages.MAX_SIDE);
        putLe24(payload, 7, 100 - 1);
        assertThatThrownBy(() -> StationPlanImages.inspect(webp("VP8X", payload)))
                .isInstanceOf(StationPlanException.class)
                .hasMessageContaining("8193");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> StationPlanImages.inspect(new byte[0]))
                .isInstanceOf(StationPlanException.class);
    }

    private static byte[] encode(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, out)).isTrue();
        return out.toByteArray();
    }

    /** Минимальный файл WebP: RIFF-заголовок и первый блок с заданным содержимым */
    private static byte[] webp(String chunk, byte[] payload) {
        byte[] data = new byte[20 + payload.length];
        put(data, 0, "RIFF");
        int riffSize = data.length - 8;
        data[4] = (byte) riffSize;
        data[5] = (byte) (riffSize >> 8);
        put(data, 8, "WEBP");
        put(data, 12, chunk);
        data[16] = (byte) payload.length;
        System.arraycopy(payload, 0, data, 20, payload.length);
        return data;
    }

    private static void put(byte[] data, int offset, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, data, offset, bytes.length);
    }

    private static void putLe24(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >> 8);
        data[offset + 2] = (byte) (value >> 16);
    }
}
