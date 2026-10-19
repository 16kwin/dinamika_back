package com.example.dinamika_back.service;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Разбор фрагментированного MP4, который FFmpeg пишет в stdout для живого просмотра.
 *
 * <p>Поток устроен так: {@code ftyp} и {@code moov} — заголовок (init-сегмент, его браузер получает первым),
 * дальше пары {@code moof} + {@code mdat} — по одной на кадр. Отсюда нужно немногое: строка кодека для
 * MediaSource в браузере, признак опорного кадра у фрагмента (с него может начать новый зритель)
 * и число кадров во фрагменте — для счётчика к/с.
 */
public final class Fmp4 {

    /** Самый большой бокс, который принимается из потока. Кадр 1080p редко больше мегабайта */
    static final int MAX_BOX_BYTES = 32 * 1024 * 1024;

    /** Бит sample_is_non_sync_sample во флагах сэмпла: 0 — опорный кадр */
    private static final int NON_SYNC = 0x0001_0000;

    private Fmp4() {
    }

    /** Бокс целиком, вместе с заголовком: так его можно сразу отдать браузеру */
    public record Box(String type, byte[] bytes) {
    }

    /** Фрагмент кадра для зрителей: moof + mdat */
    public record Fragment(byte[] bytes, boolean keyframe, int samples) {
    }

    /** Чтение боксов верхнего уровня из потока. {@code null} — поток закончился ровно между боксами */
    public static Box readBox(InputStream in) throws IOException {
        byte[] header = new byte[8];
        int first = in.read();
        if (first < 0) return null;
        header[0] = (byte) first;
        readFully(in, header, 1, 7);
        long size = u32(header, 0);
        String type = new String(header, 4, 4, StandardCharsets.ISO_8859_1);
        int headerSize = 8;
        byte[] large = null;
        if (size == 1) {
            large = new byte[8];
            readFully(in, large, 0, 8);
            size = u64(large, 0);
            headerSize = 16;
        }
        if (size < headerSize || size > MAX_BOX_BYTES) {
            throw new IOException("Некорректный размер бокса " + type + ": " + size);
        }
        byte[] bytes = new byte[(int) size];
        System.arraycopy(header, 0, bytes, 0, 8);
        if (large != null) System.arraycopy(large, 0, bytes, 8, 8);
        readFully(in, bytes, headerSize, (int) size - headerSize);
        return new Box(type, bytes);
    }

    /** Флаги сэмплов по умолчанию из moov/mvex/trex; 0, если их нет */
    public static int trexDefaultFlags(byte[] init) {
        int moov = findChild(init, 0, init.length, "moov");
        if (moov < 0) return 0;
        int mvex = findChild(init, payload(init, moov), end(init, moov), "mvex");
        if (mvex < 0) return 0;
        int trex = findChild(init, payload(init, mvex), end(init, mvex), "trex");
        if (trex < 0) return 0;
        // version/flags(4) track_ID(4) default_sample_description_index(4) duration(4) size(4) flags(4)
        return (int) u32(init, payload(init, trex) + 20);
    }

    /**
     * Опорный ли первый кадр фрагмента. Флаги берутся по цепочке, как у декодера:
     * trun first_sample_flags → флаги первого сэмпла в trun → tfhd default → trex default.
     */
    public static boolean isKeyframe(byte[] moof, int trexDefaultFlags) {
        int traf = findChild(moof, payload(moof, 0), moof.length, "traf");
        if (traf < 0) return false;
        int flags = trexDefaultFlags;

        int tfhd = findChild(moof, payload(moof, traf), end(moof, traf), "tfhd");
        if (tfhd >= 0) {
            int p = payload(moof, tfhd);
            int tfFlags = (int) (u32(moof, p) & 0xFFFFFF);
            int q = p + 8; // version/flags + track_ID
            if ((tfFlags & 0x01) != 0) q += 8; // base_data_offset
            if ((tfFlags & 0x02) != 0) q += 4; // sample_description_index
            if ((tfFlags & 0x08) != 0) q += 4; // default_sample_duration
            if ((tfFlags & 0x10) != 0) q += 4; // default_sample_size
            if ((tfFlags & 0x20) != 0) flags = (int) u32(moof, q);
        }

        int trun = findChild(moof, payload(moof, traf), end(moof, traf), "trun");
        if (trun >= 0) {
            int p = payload(moof, trun);
            int trFlags = (int) (u32(moof, p) & 0xFFFFFF);
            int q = p + 8; // version/flags + sample_count
            if ((trFlags & 0x001) != 0) q += 4; // data_offset
            if ((trFlags & 0x004) != 0) {
                flags = (int) u32(moof, q);
            } else if ((trFlags & 0x400) != 0) {
                // Флаги первого сэмпла идут после его длительности и размера, если они есть
                if ((trFlags & 0x100) != 0) q += 4;
                if ((trFlags & 0x200) != 0) q += 4;
                flags = (int) u32(moof, q);
            }
        }
        return (flags & NON_SYNC) == 0;
    }

    /** Число кадров во фрагменте (sample_count в trun) */
    public static int sampleCount(byte[] moof) {
        int traf = findChild(moof, payload(moof, 0), moof.length, "traf");
        if (traf < 0) return 0;
        int trun = findChild(moof, payload(moof, traf), end(moof, traf), "trun");
        return trun < 0 ? 0 : (int) u32(moof, payload(moof, trun) + 4);
    }

    /**
     * Строка кодека для MediaSource: {@code hev1.1.6.L120.90} для H.265, {@code avc1.640028} для H.264.
     * Собирается из hvcC/avcC init-сегмента по ISO/IEC 14496-15. {@code null}, если кодек не распознан.
     */
    public static String codecString(byte[] init) {
        int moov = findChild(init, 0, init.length, "moov");
        if (moov < 0) return null;
        int trak = findChild(init, payload(init, moov), end(init, moov), "trak");
        int mdia = trak < 0 ? -1 : findChild(init, payload(init, trak), end(init, trak), "mdia");
        int minf = mdia < 0 ? -1 : findChild(init, payload(init, mdia), end(init, mdia), "minf");
        int stbl = minf < 0 ? -1 : findChild(init, payload(init, minf), end(init, minf), "stbl");
        int stsd = stbl < 0 ? -1 : findChild(init, payload(init, stbl), end(init, stbl), "stsd");
        if (stsd < 0) return null;

        int entry = payload(init, stsd) + 8; // version/flags + entry_count
        if (entry + 8 > end(init, stsd)) return null;
        String entryType = new String(init, entry + 4, 4, StandardCharsets.ISO_8859_1);
        // VisualSampleEntry: 8 байт заголовка + 78 байт полей, дальше дочерние боксы (hvcC, avcC, …)
        int children = entry + 8 + 78;
        int entryEnd = entry + (int) u32(init, entry);

        if (entryType.equals("hev1") || entryType.equals("hvc1")) {
            int hvcC = findChild(init, children, entryEnd, "hvcC");
            return hvcC < 0 ? entryType : entryType + "." + hevcSuffix(init, payload(init, hvcC));
        }
        if (entryType.equals("avc1") || entryType.equals("avc3")) {
            int avcC = findChild(init, children, entryEnd, "avcC");
            if (avcC < 0) return entryType;
            int p = payload(init, avcC);
            return String.format("%s.%02X%02X%02X", entryType, init[p + 1] & 0xFF, init[p + 2] & 0xFF, init[p + 3] & 0xFF);
        }
        return null;
    }

    /** Хвост строки кодека HEVC: профиль, флаги совместимости, ярус и уровень, флаги ограничений */
    private static String hevcSuffix(byte[] b, int p) {
        int profileByte = b[p + 1] & 0xFF;
        int profileSpace = profileByte >> 6;
        boolean highTier = (profileByte & 0x20) != 0;
        int profileIdc = profileByte & 0x1F;
        int compatibility = (int) u32(b, p + 2);
        int level = b[p + 12] & 0xFF;

        StringBuilder out = new StringBuilder();
        if (profileSpace > 0) out.append((char) ('A' + profileSpace - 1));
        out.append(profileIdc)
                .append('.').append(Integer.toHexString(Integer.reverse(compatibility)).toUpperCase())
                .append('.').append(highTier ? 'H' : 'L').append(level);
        // Шесть байт флагов ограничений; нулевые байты в конце не пишутся
        int last = 5;
        while (last >= 0 && b[p + 6 + last] == 0) last--;
        for (int i = 0; i <= last; i++) out.append('.').append(String.format("%02X", b[p + 6 + i] & 0xFF));
        return out.toString();
    }

    // ==================== Служебное ====================

    /** Смещение дочернего бокса нужного типа в диапазоне [from, to) или -1 */
    static int findChild(byte[] b, int from, int to, String type) {
        int off = from;
        while (off + 8 <= to) {
            long size = u32(b, off);
            if (size == 1 && off + 16 <= to) size = u64(b, off + 8);
            if (size == 0) size = to - off;
            if (size < 8 || off + size > to) return -1;
            if (type.equals(new String(b, off + 4, 4, StandardCharsets.ISO_8859_1))) return off;
            off += (int) size;
        }
        return -1;
    }

    /** Начало содержимого бокса — после заголовка 8 или 16 байт */
    private static int payload(byte[] b, int box) {
        return box + (u32(b, box) == 1 ? 16 : 8);
    }

    private static int end(byte[] b, int box) {
        long size = u32(b, box);
        if (size == 1) size = u64(b, box + 8);
        return box + (int) size;
    }

    static long u32(byte[] b, int off) {
        return ((b[off] & 0xFFL) << 24) | ((b[off + 1] & 0xFFL) << 16) | ((b[off + 2] & 0xFFL) << 8) | (b[off + 3] & 0xFFL);
    }

    private static long u64(byte[] b, int off) {
        return (u32(b, off) << 32) | u32(b, off + 4);
    }

    private static void readFully(InputStream in, byte[] buffer, int offset, int length) throws IOException {
        int done = 0;
        while (done < length) {
            int n = in.read(buffer, offset + done, length - done);
            if (n < 0) throw new EOFException("Поток FFmpeg оборвался посреди бокса");
            done += n;
        }
    }

    /** Склейка moof и mdat в один фрагмент */
    static byte[] concat(byte[] a, byte[] b) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(a.length + b.length);
        out.writeBytes(a);
        out.writeBytes(b);
        return out.toByteArray();
    }
}
