package com.example.dinamika_back.features.net;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Сеть CIDR или один адрес (IPv4 и IPv6) из параметров потолка: {@code 10.0.5.0/24}, {@code 10.0.6.17},
 * {@code fd00::/8}, {@code ::1}. Разбирается только запись цифрами: имена узлов не принимаются и не разрешаются —
 * правила доступа не должны зависеть от DNS. Адрес IPv4 в форме IPv6 ({@code ::ffff:10.0.5.7}) сравнивается как IPv4.
 *
 * <p>Используют правила доступа без входа ({@code awms.security.anonymous.allowed-networks}) и доверенные прокси
 * ({@code awms.security.trusted-proxies}); проверка записи — при разборе потолка (CeilingParam), ошибка отменяет
 * запуск и называет ключ.
 */
public final class IpNetwork {

    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");
    private static final byte[] MAPPED_PREFIX = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF};

    private final byte[] address;
    private final int prefix;
    private final String text;

    private IpNetwork(byte[] address, int prefix, String text) {
        this.address = address;
        this.prefix = prefix;
        this.text = text;
    }

    /** Сеть или адрес из записи потолка; ошибка записи — null */
    public static IpNetwork parse(String value) {
        if (value == null) return null;
        String text = value.trim();
        int slash = text.indexOf('/');
        byte[] bytes = literal(slash < 0 ? text : text.substring(0, slash));
        if (bytes == null) return null;
        int bits = bytes.length * 8;
        int prefix = bits;
        if (slash >= 0) {
            String tail = text.substring(slash + 1);
            if (tail.isEmpty() || tail.length() > 3 || !tail.chars().allMatch(c -> c >= '0' && c <= '9')) return null;
            prefix = Integer.parseInt(tail);
            // /96 и больше у IPv4 в форме IPv6 — та же сеть IPv4; меньше — сеть IPv6, IPv4 в неё не попадает
            if (bytes.length == 4 && isMappedForm(text.substring(0, slash))) {
                if (prefix > 128 || prefix < 96) return null;
                prefix -= 96;
            } else if (prefix > bits) {
                return null;
            }
        }
        return new IpNetwork(bytes, prefix, text);
    }

    /** Адрес в сети; IPv4 и IPv6 между собой не совпадают (кроме IPv4 в форме IPv6) */
    public boolean contains(byte[] candidate) {
        if (candidate == null || candidate.length != address.length) return false;
        int full = prefix / 8;
        for (int i = 0; i < full; i++) {
            if (candidate[i] != address[i]) return false;
        }
        int rest = prefix % 8;
        if (rest == 0) return true;
        int mask = (0xFF << (8 - rest)) & 0xFF;
        return (candidate[full] & mask) == (address[full] & mask);
    }

    @Override
    public String toString() {
        return text;
    }

    /**
     * Байты адреса из записи цифрами или null; DNS не используется. Принимает IPv4, IPv6, IPv6 в квадратных
     * скобках и с зоной ({@code fe80::1%eth0} — зона отбрасывается); IPv4 в форме IPv6 возвращается четырьмя байтами
     */
    public static byte[] literal(String value) {
        if (value == null) return null;
        String text = value.trim();
        if (text.startsWith("[") && text.endsWith("]")) text = text.substring(1, text.length() - 1);
        int zone = text.indexOf('%');
        if (zone >= 0) text = text.substring(0, zone);
        if (text.isEmpty()) return null;
        Matcher v4 = IPV4.matcher(text);
        if (v4.matches()) {
            byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                int octet = Integer.parseInt(v4.group(i + 1));
                if (octet > 255) return null;
                bytes[i] = (byte) octet;
            }
            return bytes;
        }
        // IPv6: только запись с «:» из шестнадцатеричных цифр ASCII и точек, начиная с цифры или «:». Такую строку
        // InetAddress разбирает как адрес или отклоняет, но в DNS не идёт (иначе «.:» из заголовка ушло бы в DNS)
        char first = text.charAt(0);
        if (text.indexOf(':') < 0 || !(first == ':' || isHexDigit(first))
                || !text.chars().allMatch(c -> c == ':' || c == '.' || isHexDigit(c))) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = InetAddress.getByName(text).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
        if (bytes.length == 16 && Arrays.equals(Arrays.copyOf(bytes, 12), MAPPED_PREFIX)) {
            return Arrays.copyOfRange(bytes, 12, 16);
        }
        return bytes;
    }

    private static boolean isHexDigit(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /** Запись IPv4 в форме IPv6 ({@code ::ffff:10.0.0.0}): InetAddress сам превращает её в IPv4 */
    private static boolean isMappedForm(String text) {
        return text.indexOf(':') >= 0;
    }
}
