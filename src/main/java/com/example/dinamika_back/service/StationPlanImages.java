package com.example.dinamika_back.service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * Проверка картинки плана цеха: формат — по сигнатуре байтов (расширению и Content-Type не верим),
 * размеры — из заголовка, без декодирования всей картинки. PNG и JPEG читает ImageIO,
 * WebP ImageIO не знает — его заголовок RIFF (VP8, VP8L, VP8X) разбирается здесь.
 */
public final class StationPlanImages {

    /** 20 МБ */
    public static final long MAX_BYTES = 20L * 1024 * 1024;
    /** Наибольшая сторона картинки, px */
    public static final int MAX_SIDE = 8192;

    public enum Format {
        PNG("image/png", "png"),
        JPEG("image/jpeg", "jpg"),
        WEBP("image/webp", "webp");

        private final String mime;
        private final String extension;

        Format(String mime, String extension) {
            this.mime = mime;
            this.extension = extension;
        }

        public String mime() {
            return mime;
        }

        public String extension() {
            return extension;
        }
    }

    /** Формат и размеры картинки */
    public record Info(Format format, int width, int height) {
    }

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private StationPlanImages() {
    }

    /** Формат по первым байтам файла или null, если это не PNG, JPEG или WebP */
    public static Format detect(byte[] data) {
        if (data == null) return null;
        if (startsWith(data, 0, PNG_SIGNATURE)) return Format.PNG;
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) return Format.JPEG;
        if (data.length >= 12 && ascii(data, 0, "RIFF") && ascii(data, 8, "WEBP")) return Format.WEBP;
        return null;
    }

    /** Формат и размеры с проверкой лимитов; любое несоответствие — 400 с понятной причиной */
    public static Info inspect(byte[] data) {
        if (data == null || data.length == 0) throw StationPlanException.badRequest("Файл пустой");
        if (data.length > MAX_BYTES) throw StationPlanException.tooLarge("Файл больше 20 МБ");
        Format format = detect(data);
        if (format == null) throw StationPlanException.badRequest("Поддерживаются только PNG, JPEG и WebP");
        int[] size = format == Format.WEBP ? webpSize(data) : imageIoSize(data);
        if (size == null || size[0] <= 0 || size[1] <= 0) {
            throw StationPlanException.badRequest("Не удалось прочитать размеры картинки — файл повреждён");
        }
        if (size[0] > MAX_SIDE || size[1] > MAX_SIDE) {
            throw StationPlanException.badRequest("Картинка " + size[0] + "×" + size[1]
                    + " px — больше допустимых " + MAX_SIDE + " px по стороне");
        }
        return new Info(format, size[0], size[1]);
    }

    /**
     * Размеры WebP из заголовка: {ширина, высота} или null.
     * VP8 — ключевой кадр с кодом 9D 01 2A и 14-битными размерами; VP8L — байт 0x2F и два 14-битных поля
     * «размер − 1»; VP8X — 24-битные «размер − 1» холста.
     */
    public static int[] webpSize(byte[] data) {
        if (data == null || data.length < 30 || !ascii(data, 0, "RIFF") || !ascii(data, 8, "WEBP")) return null;
        if (ascii(data, 12, "VP8 ")) {
            // Байты 20–22 — заголовок кадра: младший бит 0 — ключевой кадр
            if ((data[20] & 0x01) != 0) return null;
            if ((data[23] & 0xFF) != 0x9D || (data[24] & 0xFF) != 0x01 || (data[25] & 0xFF) != 0x2A) return null;
            int width = le16(data, 26) & 0x3FFF;
            int height = le16(data, 28) & 0x3FFF;
            return new int[]{width, height};
        }
        if (ascii(data, 12, "VP8L")) {
            if ((data[20] & 0xFF) != 0x2F) return null;
            long bits = le32(data, 21);
            int width = (int) (bits & 0x3FFF) + 1;
            int height = (int) ((bits >> 14) & 0x3FFF) + 1;
            return new int[]{width, height};
        }
        if (ascii(data, 12, "VP8X")) {
            int width = le24(data, 24) + 1;
            int height = le24(data, 27) + 1;
            return new int[]{width, height};
        }
        return null;
    }

    /** Размеры PNG и JPEG через ImageIO — читается только заголовок */
    public static int[] imageIoSize(byte[] data) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (in == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (data.length < offset + prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) return false;
        }
        return true;
    }

    private static boolean ascii(byte[] data, int offset, String text) {
        return startsWith(data, offset, text.getBytes(StandardCharsets.US_ASCII));
    }

    private static int le16(byte[] data, int offset) {
        return (data[offset] & 0xFF) | (data[offset + 1] & 0xFF) << 8;
    }

    private static int le24(byte[] data, int offset) {
        return le16(data, offset) | (data[offset + 2] & 0xFF) << 16;
    }

    private static long le32(byte[] data, int offset) {
        return (le24(data, offset) | (long) (data[offset + 3] & 0xFF) << 24) & 0xFFFFFFFFL;
    }
}
