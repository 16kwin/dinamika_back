package com.example.dinamika_back.service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ссылки на внешние файлы в glTF и GLB. Просмотрщик (three.js GLTFLoader) сам загружает buffers[].uri и images[].uri:
 * адрес в интернете или во внутренней сети запрашивался бы у каждого, кто откроет модель, а относительный путь —
 * с нашего сервера с cookie пользователя. Поэтому принимаются только встроенные данные (data:) или блок BIN в GLB.
 *
 * <p>JSON читается потоком своим небольшим разборщиком: строки data: в .gltf бывают по сотне мегабайт,
 * целиком в память они не попадают. Без Spring — проверяется обычными тестами.
 */
public final class NomenclatureModelGltf {

    private static final long JSON_CHUNK = 0x4E4F534AL;
    /** Глубже настоящий glTF не бывает; ограничение защищает разбор от переполнения стека */
    private static final int MAX_DEPTH = 512;
    /** Сколько символов ссылки показать в отказе */
    private static final int URI_PREVIEW = 80;
    /** Имена полей длиннее не совпадут ни с одним искомым (buffers, images, uri) */
    private static final int KEY_CAPTURE = 16;
    private static final int MAX_CHUNKS = 1024;
    private static final int BUFFER_SIZE = 64 * 1024;

    private NomenclatureModelGltf() {
    }

    /**
     * GLB: проверяются все блоки JSON — three.js берёт последний из них.
     * null — ссылок наружу нет, иначе текст отказа (со строчной буквы, без указания файла).
     */
    public static String glbProblem(Path file) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(file)) {
            long size = channel.size();
            long offset = 12;
            int chunks = 0;
            ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            while (offset + 8 <= size) {
                // В настоящем GLB два блока (JSON и BIN); тысячи крошечных блоков — порча или попытка занять сервер
                if (++chunks > MAX_CHUNKS) return broken("слишком много блоков в GLB");
                header.clear();
                channel.position(offset);
                while (header.hasRemaining()) {
                    if (channel.read(header) < 0) return broken("файл обрезан");
                }
                long length = header.getInt(0) & 0xFFFFFFFFL;
                long type = header.getInt(4) & 0xFFFFFFFFL;
                long start = offset + 8;
                if (type == JSON_CHUNK) {
                    if (start + length > size) return broken("блок JSON обрезан");
                    channel.position(start);
                    // Поток канала не закрываем: канал закроет try-with-resources
                    Reader reader = new InputStreamReader(
                            new LimitedInputStream(Channels.newInputStream(channel), length), StandardCharsets.UTF_8);
                    String problem = jsonProblem(reader, (int) Math.min(BUFFER_SIZE, Math.max(length, 16)));
                    if (problem != null) return problem;
                }
                offset = start + length;
            }
        }
        return null;
    }

    /** Текстовый .gltf: весь файл — JSON. null — ссылок наружу нет, иначе текст отказа */
    public static String gltfProblem(Path file) throws IOException {
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            return jsonProblem(reader, BUFFER_SIZE);
        }
    }

    /** Разбор JSON glTF: null — ссылок наружу нет, иначе текст отказа */
    static String jsonProblem(Reader reader, int bufferSize) throws IOException {
        try {
            new Scanner(reader, bufferSize).run();
            return null;
        } catch (Rejected e) {
            return e.getMessage();
        }
    }

    private static String broken(String reason) {
        return "описание модели glTF (JSON) повреждено — " + reason;
    }

    /** Отказ найден — разбор дальше не нужен */
    private static final class Rejected extends Exception {
        Rejected(String message) {
            super(message, null, false, false);
        }
    }

    /** Где в glTF находится текущее значение: важны только buffers/images верхнего уровня (массивы) и их поле uri */
    private enum Place { ROOT, REFS, REF, URI, OTHER }

    /**
     * Строгий разбор JSON (RFC 8259) за один проход. Всё, что JSON.parse в браузере прочитает, читается так же;
     * при повторе имени поля проверяются все повторы (браузер берёт последний).
     */
    private static final class Scanner {

        private final Reader reader;
        private final char[] buffer;
        private int position;
        private int limit;

        Scanner(Reader reader, int bufferSize) {
            this.reader = reader;
            this.buffer = new char[bufferSize];
        }

        void run() throws IOException, Rejected {
            // TextDecoder в браузере отбрасывает BOM в начале
            if (peek() == '﻿') next();
            value(0, Place.ROOT, null);
            // Что после корневого значения — не важно: при мусоре JSON.parse откажет, и модель не загрузится вовсе
        }

        private void value(int depth, Place place, String path) throws IOException, Rejected {
            if (depth > MAX_DEPTH) throw new Rejected(broken("слишком глубокая вложенность"));
            skipWhitespace();
            int c = peek();
            if (place == Place.URI && c != '"') {
                throw new Rejected("некорректная ссылка на файл в " + path + " — ожидалась строка");
            }
            // По схеме glTF 2.0 — только массив. Объект {"0": {...}} three.js индексирует так же (json.buffers[0]),
            // а его поля здесь не считались бы элементами — ссылка прошла бы без проверки
            if (place == Place.REFS && c != '[') {
                throw new Rejected(broken("раздел " + path + " должен быть массивом"));
            }
            if (c == '{') {
                object(depth, place, path);
            } else if (c == '[') {
                array(depth, place, path);
            } else if (c == '"') {
                String text = string(place == Place.URI ? URI_PREVIEW : 0, place == Place.URI);
                if (place == Place.URI) checkUri(text, path);
            } else if (c == 't') {
                literal("true");
            } else if (c == 'f') {
                literal("false");
            } else if (c == 'n') {
                literal("null");
            } else if (c == '-' || isDigit(c)) {
                number();
            } else {
                throw new Rejected(broken(c < 0 ? "файл обрывается" : "неожиданный символ"));
            }
        }

        private void object(int depth, Place place, String path) throws IOException, Rejected {
            next();
            skipWhitespace();
            if (peek() == '}') {
                next();
                return;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') throw new Rejected(broken("ожидалось имя поля"));
                String key = string(KEY_CAPTURE, false);
                skipWhitespace();
                if (next() != ':') throw new Rejected(broken("ожидалось «:» после имени поля"));
                Place child = Place.OTHER;
                String childPath = null;
                if (place == Place.ROOT && ("buffers".equals(key) || "images".equals(key))) {
                    child = Place.REFS;
                    childPath = key;
                } else if (place == Place.REF && "uri".equals(key)) {
                    child = Place.URI;
                    childPath = path + ".uri";
                }
                value(depth + 1, child, childPath);
                skipWhitespace();
                int c = next();
                if (c == '}') return;
                if (c != ',') throw new Rejected(broken("ожидалась «,» или «}»"));
            }
        }

        private void array(int depth, Place place, String path) throws IOException, Rejected {
            next();
            skipWhitespace();
            if (peek() == ']') {
                next();
                return;
            }
            for (int index = 0; ; index++) {
                boolean refs = place == Place.REFS;
                value(depth + 1, refs ? Place.REF : Place.OTHER, refs ? path + "[" + index + "]" : null);
                skipWhitespace();
                int c = next();
                if (c == ']') return;
                if (c != ',') throw new Rejected(broken("ожидалась «,» или «]»"));
            }
        }

        /**
         * Строка JSON. capture — сколько первых символов вернуть (0 — ничего, строка только пропускается);
         * skipLeading — не считать начальные пробелы и управляющие символы (их отбрасывает и разбор URL в браузере).
         */
        private String string(int capture, boolean skipLeading) throws IOException, Rejected {
            next();
            StringBuilder text = capture > 0 ? new StringBuilder() : null;
            boolean leading = skipLeading;
            while (true) {
                int c = next();
                if (c < 0) throw new Rejected(broken("строка не закрыта"));
                if (c == '"') break;
                if (c < 0x20) throw new Rejected(broken("управляющий символ внутри строки"));
                if (c == '\\') c = escape();
                if (text == null) continue;
                if (leading && c <= 0x20) continue;
                leading = false;
                if (text.length() <= capture) text.append((char) c);
            }
            return text == null ? null : text.toString();
        }

        private int escape() throws IOException, Rejected {
            int c = next();
            switch (c) {
                case '"', '\\', '/':
                    return c;
                case 'b':
                    return '\b';
                case 'f':
                    return '\f';
                case 'n':
                    return '\n';
                case 'r':
                    return '\r';
                case 't':
                    return '\t';
                case 'u':
                    int value = 0;
                    for (int i = 0; i < 4; i++) {
                        int digit = hex(next());
                        if (digit < 0) throw new Rejected(broken("неверная последовательность \\u"));
                        value = value * 16 + digit;
                    }
                    return value;
                default:
                    throw new Rejected(broken("неверная escape-последовательность"));
            }
        }

        private void literal(String word) throws IOException, Rejected {
            for (int i = 0; i < word.length(); i++) {
                if (next() != word.charAt(i)) throw new Rejected(broken("неожиданный символ"));
            }
        }

        private void number() throws IOException, Rejected {
            if (peek() == '-') next();
            digits();
            if (peek() == '.') {
                next();
                digits();
            }
            if (peek() == 'e' || peek() == 'E') {
                next();
                if (peek() == '+' || peek() == '-') next();
                digits();
            }
        }

        private void digits() throws IOException, Rejected {
            if (!isDigit(peek())) throw new Rejected(broken("неверное число"));
            while (isDigit(peek())) next();
        }

        /** Принимаются только встроенные данные data: — схему URL браузер сравнивает без учёта регистра */
        private static void checkUri(String uri, String path) throws Rejected {
            if (uri.regionMatches(true, 0, "data:", 0, 5)) return;
            String preview = uri.length() > URI_PREVIEW ? uri.substring(0, URI_PREVIEW) + "…" : uri;
            preview = preview.replaceAll("\\p{Cntrl}", "?");
            throw new Rejected("ссылка на внешний файл «" + preview + "» в " + path
                    + " — просмотрщик запрашивал бы этот адрес у каждого, кто откроет модель. "
                    + "Сохраните модель одним файлом GLB или glTF со встроенными текстурами и буферами");
        }

        private void skipWhitespace() throws IOException {
            while (true) {
                int c = peek();
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') return;
                next();
            }
        }

        private int peek() throws IOException {
            if (position == limit) {
                int read = reader.read(buffer, 0, buffer.length);
                position = 0;
                limit = Math.max(read, 0);
                if (read <= 0) return -1;
            }
            return buffer[position];
        }

        private int next() throws IOException {
            int c = peek();
            if (c >= 0) position++;
            return c;
        }

        private static boolean isDigit(int c) {
            return c >= '0' && c <= '9';
        }

        private static int hex(int c) {
            if (c >= '0' && c <= '9') return c - '0';
            if (c >= 'a' && c <= 'f') return c - 'a' + 10;
            if (c >= 'A' && c <= 'F') return c - 'A' + 10;
            return -1;
        }
    }

    /** Поток не длиннее limit байт — блок JSON внутри GLB. Закрытие не закрывает канал под ним */
    private static final class LimitedInputStream extends FilterInputStream {

        private long remaining;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = super.read();
            if (b >= 0) remaining--;
            return b;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (remaining <= 0) return -1;
            int read = super.read(bytes, offset, (int) Math.min(length, remaining));
            if (read > 0) remaining -= read;
            return read;
        }

        @Override
        public void close() {
        }
    }
}
