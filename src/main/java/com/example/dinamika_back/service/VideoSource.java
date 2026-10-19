package com.example.dinamika_back.service;

import com.example.dinamika_back.features.CeilingParam;
import com.example.dinamika_back.features.FeatureConfigurationException;
import com.example.dinamika_back.features.FeatureSnapshot;

import java.io.ByteArrayOutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Строка подключения камеры → аргументы входа FFmpeg.
 *
 * <p>Поддерживаются три вида строк:
 * <ul>
 *   <li>{@code rtsp://логин:пароль@ip:554/путь} — настоящая камера. Пароль может содержать «@»
 *       (у камер стенда так и есть), поэтому логин и пароль отделяются от адреса по последнему «@»
 *       и перед передачей в FFmpeg кодируются: «@» → «%40». Без этого FFmpeg не разбирает ссылку.</li>
 *   <li>{@code test:} — тестовый сигнал FFmpeg вместо камеры. Нужен для разработки и проверки
 *       записи без подключённых камер.</li>
 *   <li>{@code file:C:/путь/запись.mp4} — эмулятор камеры: запись с настоящей камеры по кругу в реальном
 *       времени, без перекодирования. Для нагрузочной проверки живого потока и записи без камер.</li>
 * </ul>
 *
 * <p>Что FFmpeg разрешено открыть, задаёт потолок установки ({@link SourcePolicy}): {@code test:} и {@code file:}
 * по умолчанию запрещены, адрес камеры сверяется с разрешёнными сетями в момент запуска FFmpeg (раздел 6.5 проекта).
 *
 * <p>Транспорт RTSP по умолчанию — TCP. Камере, которая обрывает TCP-сессию через пару секунд
 * (так ведёт себя станционная камера стенда), в конец строки дописывается {@code ?transport=udp}.
 * Параметр читается здесь и камере не передаётся.
 */
public final class VideoSource {

    /** Префикс тестового источника */
    public static final String TEST_PREFIX = "test:";

    /** Префикс эмулятора камеры: {@code file:C:/путь/к/записи.mp4} */
    public static final String FILE_PREFIX = "file:";

    /** Параметр строки подключения, выбирающий транспорт RTSP: {@code ?transport=udp} */
    public static final String TRANSPORT_PARAM = "transport";

    /** Пароль в ссылках, которые получает страница */
    public static final String PASSWORD_MASK = "***";

    /** Порт RTSP, если в ссылке он не указан */
    private static final int DEFAULT_RTSP_PORT = 554;

    /** Тестовый сигнал: движущаяся таблица 1280×720, 25 кадров в секунду */
    private static final String TEST_FILTER = "testsrc2=size=1280x720:rate=25";

    /** Протоколы FFmpeg для камеры: управление RTSP и RTP по UDP или внутри TCP-сессии. Другие FFmpeg не откроет */
    static final String NETWORK_PROTOCOLS = "rtsp,rtp,tcp,udp";

    /** Протокол эмулятора камеры: только локальный файл, а не http:, concat: и подобные под видом пути */
    static final String FILE_PROTOCOLS = "file";

    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

    private VideoSource() {
    }

    // ==================== Политика источников (потолок установки) ====================

    /**
     * Что FFmpeg разрешено открыть на этой установке — параметры потолка (раздел 4.5 проекта).
     *
     * @param allowFileSource {@code awms.video.allow-file-source}: источники {@code file:} и {@code test:}
     * @param networks        {@code awms.video.allowed-networks}: сети, в которых могут быть камеры
     */
    public record SourcePolicy(boolean allowFileSource, List<Network> networks) {

        public SourcePolicy {
            networks = List.copyOf(networks);
        }

        /** Политика из снимка потолка. Сеть, записанная с ошибкой, отменяет запуск модуля видео */
        public static SourcePolicy of(FeatureSnapshot ceiling) {
            List<Network> networks = new ArrayList<>();
            for (String item : ceiling.listParam(CeilingParam.VIDEO_ALLOWED_NETWORKS)) {
                Network network = Network.parse(item);
                if (network == null) {
                    throw new FeatureConfigurationException("Параметр потолка "
                            + CeilingParam.VIDEO_ALLOWED_NETWORKS.key() + ": «" + item + "» ("
                            + ceiling.paramOrigin(CeilingParam.VIDEO_ALLOWED_NETWORKS)
                            + ") — ожидается сеть вида 192.168.0.0/16 или адрес камеры");
                }
                networks.add(network);
            }
            return new SourcePolicy(ceiling.boolParam(CeilingParam.VIDEO_ALLOW_FILE_SOURCE), networks);
        }

        /** Адрес в одной из разрешённых сетей */
        public boolean allows(InetAddress address) {
            return networks.stream().anyMatch(network -> network.contains(address));
        }
    }

    /** Сеть CIDR (IPv4 или IPv6): адрес и длина префикса; адрес без префикса — один узел */
    public static final class Network {

        private final byte[] address;
        private final int prefix;
        private final String text;

        private Network(byte[] address, int prefix, String text) {
            this.address = address;
            this.prefix = prefix;
            this.text = text;
        }

        /**
         * {@code 192.168.0.0/16}, {@code 10.1.2.3}, {@code fd00::/8}. Имя узла не принимается и не разрешается:
         * список сетей не должен зависеть от DNS. Ошибка записи — null
         */
        public static Network parse(String value) {
            if (value == null) return null;
            String text = value.trim();
            int slash = text.indexOf('/');
            byte[] bytes = literal(slash < 0 ? text : text.substring(0, slash));
            if (bytes == null) return null;
            int bits = bytes.length * 8;
            int prefix = bits;
            if (slash >= 0) {
                String tail = text.substring(slash + 1);
                if (tail.isEmpty() || tail.length() > 3 || !tail.chars().allMatch(Character::isDigit)) return null;
                prefix = Integer.parseInt(tail);
                if (prefix > bits) return null;
            }
            return new Network(bytes, prefix, text);
        }

        /** Адрес в сети; IPv4 и IPv6 между собой не совпадают */
        public boolean contains(InetAddress candidate) {
            byte[] other = candidate.getAddress();
            if (other.length != address.length) return false;
            int full = prefix / 8;
            for (int i = 0; i < full; i++) {
                if (other[i] != address[i]) return false;
            }
            int rest = prefix % 8;
            if (rest == 0) return true;
            int mask = (0xFF << (8 - rest)) & 0xFF;
            return (other[full] & mask) == (address[full] & mask);
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /** Разрешение имени камеры в адреса; в тестах подменяется */
    @FunctionalInterface
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    /** Байты IP-адреса из записи цифрами или null; DNS не используется */
    private static byte[] literal(String text) {
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
        // IPv6: только запись с «:» — InetAddress разбирает её без обращения к DNS
        if (text.indexOf(':') < 0 || !text.chars().allMatch(c -> c == ':' || c == '.' || Character.digit(c, 16) >= 0)) {
            return null;
        }
        try {
            return InetAddress.getByName(text).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * Проверка источника по политике установки — при сохранении камеры и перед каждым запуском FFmpeg.
     * Возвращает то, что получит FFmpeg после {@code -i}:
     * <ul>
     *   <li>{@code test:} и {@code file:} — только при {@code awms.video.allow-file-source=true}, иначе 403;</li>
     *   <li>{@code rtsp://} — имя камеры разрешается в адрес здесь же, адрес сверяется с
     *       {@code awms.video.allowed-networks}, и в ссылку для FFmpeg подставляется уже этот адрес, а не имя:
     *       подмена DNS между проверкой и подключением ничего не даёт. Вне сетей — 403, имя не разрешилось — 503;</li>
     *   <li>любая другая строка — 400: FFmpeg получает только {@code rtsp://}, {@code file:} и {@code test:}.</li>
     * </ul>
     */
    public static String checkedSource(String url, SourcePolicy policy, HostResolver resolver) {
        if (isTest(url) || isFile(url)) {
            if (!policy.allowFileSource()) {
                throw VideoException.forbidden("Источники file: и test: на этой установке запрещены "
                        + "(awms.video.allow-file-source)");
            }
            return isFile(url) ? url.trim().substring(FILE_PREFIX.length()).trim() : TEST_FILTER;
        }
        if (!isRtsp(url)) throw VideoException.badRequest("URL подключения должен начинаться с rtsp://");
        return pinnedRtsp(streamUrl(url), policy, resolver);
    }

    /**
     * Ссылка для FFmpeg (логин и пароль уже закодированы) с адресом камеры вместо имени. Адрес ищется так же,
     * как его ищет FFmpeg: до первого «/», «?» или «#», логин отделяется последним «@» внутри
     */
    private static String pinnedRtsp(String stream, SourcePolicy policy, HostResolver resolver) {
        String scheme = stream.substring(0, "rtsp://".length());
        String rest = stream.substring(scheme.length());
        int end = rest.length();
        for (char c : new char[]{'/', '?', '#'}) {
            int i = rest.indexOf(c);
            if (i >= 0 && i < end) end = i;
        }
        String authority = rest.substring(0, end);
        int at = authority.lastIndexOf('@');
        String credentials = authority.substring(0, at + 1);
        String hostPort = authority.substring(at + 1);

        String host;
        String port;
        if (hostPort.startsWith("[")) {
            int close = hostPort.indexOf(']');
            if (close < 0) throw VideoException.badRequest("Неверный адрес камеры в URL подключения");
            host = hostPort.substring(1, close);
            port = hostPort.substring(close + 1);
        } else {
            int colon = hostPort.indexOf(':');
            host = colon < 0 ? hostPort : hostPort.substring(0, colon);
            port = colon < 0 ? "" : hostPort.substring(colon);
        }
        if (host.isEmpty()) throw VideoException.badRequest("В URL подключения нет адреса камеры");
        if (!port.isEmpty() && (!port.startsWith(":") || port.length() > 6
                || !port.substring(1).chars().allMatch(c -> c >= '0' && c <= '9'))) {
            throw VideoException.badRequest("Неверный порт камеры в URL подключения");
        }

        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw VideoException.unavailable("Не удалось найти адрес камеры «" + host + "»");
        }
        InetAddress allowed = Arrays.stream(addresses).filter(policy::allows).findFirst().orElse(null);
        if (allowed == null) {
            String found = Arrays.stream(addresses).map(InetAddress::getHostAddress).collect(Collectors.joining(", "));
            throw VideoException.forbidden("Адрес камеры " + host + (found.isEmpty() || found.equals(host) ? "" : " (" + found + ")")
                    + " вне разрешённых сетей камер (awms.video.allowed-networks)");
        }
        String ip = allowed.getHostAddress();
        int scope = ip.indexOf('%');
        if (scope >= 0) ip = ip.substring(0, scope);
        String pinnedHost = allowed instanceof Inet6Address ? "[" + ip + "]" : ip;
        return scheme + credentials + pinnedHost + port + rest.substring(end);
    }

    public static boolean isTest(String url) {
        return url != null && url.trim().toLowerCase().startsWith(TEST_PREFIX);
    }

    /** Эмулятор камеры: запись с настоящей камеры по кругу, в реальном времени — для проверки без камер */
    public static boolean isFile(String url) {
        return url != null && url.trim().toLowerCase().startsWith(FILE_PREFIX);
    }

    public static boolean isRtsp(String url) {
        return url != null && url.trim().toLowerCase().startsWith("rtsp://");
    }

    /**
     * Аргументы входа FFmpeg — только после проверки источника по политике установки ({@link #checkedSource}).
     * Сетевой вход и файл открываются с {@code -protocol_whitelist}: FFmpeg не пойдёт по другим протоколам,
     * даже если на них сошлётся сам поток.
     *
     * @param realtime для записи тестового сигнала: выдавать кадры в реальном времени, а не так быстро,
     *                 как успевает процессор. Для камер не нужен — камера и так отдаёт поток вживую
     */
    public static List<String> inputArgs(String url, int timeoutSeconds, boolean realtime, SourcePolicy policy,
                                         HostResolver resolver) {
        String input = checkedSource(url, policy, resolver);
        List<String> args = new ArrayList<>();
        if (isTest(url)) {
            if (realtime) args.add("-re");
            args.addAll(List.of("-f", "lavfi", "-i", input));
            return args;
        }
        if (isFile(url)) {
            // Запись и живой поток: видео камеры по кругу и с той же скоростью, с какой его отдавала камера.
            // Кадру и проверке (ffprobe не знает -stream_loop) хватает самого файла
            if (realtime) args.addAll(List.of("-re", "-stream_loop", "-1"));
            args.addAll(List.of("-protocol_whitelist", FILE_PROTOCOLS, "-i", input));
            return args;
        }
        args.addAll(List.of("-protocol_whitelist", NETWORK_PROTOCOLS, "-rtsp_transport", transport(url),
                // Таймаут сокета в микросекундах: без него FFmpeg ждёт недоступную камеру бесконечно
                "-timeout", String.valueOf(timeoutSeconds * 1_000_000L), "-i", input));
        return args;
    }

    /**
     * Транспорт RTSP. По умолчанию TCP: на Wi-Fi и через коммутатор UDP теряет пакеты, картинка рассыпается.
     * UDP — только если он указан в строке подключения.
     */
    public static String transport(String url) {
        return queryParams(normalizeRtspUrl(url)).stream()
                .anyMatch(param -> param.equalsIgnoreCase(TRANSPORT_PARAM + "=udp")) ? "udp" : "tcp";
    }

    /** Ссылка для FFmpeg: логин и пароль закодированы, параметр transport убран — камере он не нужен */
    public static String streamUrl(String url) {
        String normalized = normalizeRtspUrl(url);
        int query = queryStart(normalized);
        if (query < 0) return normalized;
        String kept = queryParams(normalized).stream()
                .filter(param -> !param.toLowerCase().startsWith(TRANSPORT_PARAM + "="))
                .collect(Collectors.joining("&"));
        return normalized.substring(0, query) + (kept.isEmpty() ? "" : "?" + kept);
    }

    /** Параметры после «?» в RTSP-ссылке с уже закодированным паролем */
    private static List<String> queryParams(String normalized) {
        int query = queryStart(normalized);
        if (query < 0) return List.of();
        return Arrays.stream(normalized.substring(query + 1).split("&"))
                .filter(param -> !param.isEmpty())
                .toList();
    }

    /** Начало параметров. После кодирования пароля последний «@» — разделитель логина и адреса */
    private static int queryStart(String normalized) {
        if (!isRtsp(normalized)) return -1;
        return normalized.indexOf('?', Math.max(0, normalized.lastIndexOf('@')));
    }

    /** Нужно ли перекодировать видео: поток камеры пишется как есть, тестовый сигнал — кодируется в H.264 */
    public static boolean needsEncoding(String url) {
        return isTest(url);
    }

    /**
     * Кодирует логин и пароль в RTSP-ссылке. Уже закодированные символы (%40) не кодируются повторно.
     * Ссылки без логина и строки других видов возвращаются без изменений.
     */
    public static String normalizeRtspUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (!isRtsp(trimmed)) return trimmed;

        String scheme = trimmed.substring(0, "rtsp://".length());
        String rest = trimmed.substring(scheme.length());
        int pathStart = indexOfPathAfterLastAt(rest);
        String authority = pathStart < 0 ? rest : rest.substring(0, pathStart);
        String path = pathStart < 0 ? "" : rest.substring(pathStart);

        int at = authority.lastIndexOf('@');
        if (at < 0) return trimmed;

        String userInfo = authority.substring(0, at);
        String hostPort = authority.substring(at + 1);
        int colon = userInfo.indexOf(':');
        String user = colon < 0 ? userInfo : userInfo.substring(0, colon);
        String password = colon < 0 ? null : userInfo.substring(colon + 1);

        StringBuilder result = new StringBuilder(scheme).append(encode(decode(user)));
        if (password != null) result.append(':').append(encode(decode(password)));
        return result.append('@').append(hostPort).append(path).toString();
    }

    /**
     * Ссылка без пароля — для журналов, сообщений об ошибках и страницы: пароль камеры в браузер не уходит.
     * Пароль заменяется на {@link #PASSWORD_MASK}.
     */
    public static String maskPassword(String url) {
        if (!isRtsp(url)) return url;
        String normalized = normalizeRtspUrl(url);
        String rest = normalized.substring("rtsp://".length());
        // Ссылка без пути (rtsp://логин:пароль@ip:554) — «@» ищется по всей строке, иначе пароль не маскировался
        int pathStart = indexOfPathAfterLastAt(rest);
        int at = (pathStart < 0 ? rest : rest.substring(0, pathStart)).lastIndexOf('@');
        if (at < 0) return normalized;
        String userInfo = rest.substring(0, at);
        int colon = userInfo.indexOf(':');
        String user = colon < 0 ? userInfo : userInfo.substring(0, colon);
        return "rtsp://" + user + (colon < 0 ? "" : ":" + PASSWORD_MASK) + rest.substring(at);
    }

    /**
     * Страница получает ссылку с паролем «***» и присылает её обратно, если пароль не меняли —
     * тогда подставляется сохранённый пароль. Новый пароль, введённый на странице, остаётся как есть.
     *
     * <p>Сохранённый пароль подставляется только для того же сервера и логина: схема, хост (без учёта
     * регистра), порт (554, если не указан) и логин совпадают с сохранёнными. Иначе ссылка
     * {@code rtsp://admin:***@свой-сервер} отправила бы пароль камеры на чужой RTSP-сервер при проверке
     * или живом просмотре. Путь и параметры менять можно.
     *
     * @throws VideoException 400 — адрес или логин изменились, а пароль остался «***»
     */
    public static String restorePassword(String url, String stored) {
        if (!isRtsp(url)) return url;
        UserInfo incoming = userInfo(url.trim());
        if (incoming == null || !PASSWORD_MASK.equals(incoming.password())) return url;
        UserInfo saved = isRtsp(stored) ? userInfo(stored.trim()) : null;
        if (saved == null || !sameServerAndUser(incoming, saved)) {
            throw VideoException.badRequest("Адрес камеры изменился — введите пароль заново");
        }
        String password = saved.password() == null ? "" : saved.password();
        return incoming.scheme() + incoming.user() + ":" + password + "@" + incoming.hostAndPath();
    }

    private record UserInfo(String scheme, String user, String password, String hostAndPath) {
    }

    /** Хост в нижнем регистре и порт */
    private record Endpoint(String host, int port) {
    }

    private static boolean sameServerAndUser(UserInfo incoming, UserInfo saved) {
        Endpoint a = endpoint(incoming.hostAndPath());
        Endpoint b = endpoint(saved.hostAndPath());
        return a != null && a.equals(b)
                && incoming.scheme().equalsIgnoreCase(saved.scheme())
                && decode(incoming.user()).equals(decode(saved.user()));
    }

    /**
     * Хост и порт из «адрес[:порт]/путь?параметры». Адрес кончается на первом «/», «?» или «#» — так же
     * ссылку разбирает FFmpeg. Порт не числом — null: такой адрес не считается совпавшим.
     */
    private static Endpoint endpoint(String hostAndPath) {
        int end = hostAndPath.length();
        for (char c : new char[]{'/', '?', '#'}) {
            int i = hostAndPath.indexOf(c);
            if (i >= 0 && i < end) end = i;
        }
        String hostPort = hostAndPath.substring(0, end);
        String host;
        String port;
        if (hostPort.startsWith("[")) {
            // IPv6: [адрес]:порт
            int close = hostPort.indexOf(']');
            if (close < 0) return null;
            host = hostPort.substring(0, close + 1);
            String tail = hostPort.substring(close + 1);
            if (!tail.isEmpty() && !tail.startsWith(":")) return null;
            port = tail.isEmpty() ? "" : tail.substring(1);
        } else {
            int colon = hostPort.indexOf(':');
            host = colon < 0 ? hostPort : hostPort.substring(0, colon);
            port = colon < 0 ? "" : hostPort.substring(colon + 1);
        }
        if (port.isEmpty()) return new Endpoint(host.toLowerCase(Locale.ROOT), DEFAULT_RTSP_PORT);
        if (port.length() > 5 || !port.chars().allMatch(c -> c >= '0' && c <= '9')) return null;
        return new Endpoint(host.toLowerCase(Locale.ROOT), Integer.parseInt(port));
    }

    /** rtsp://логин:пароль@адрес/путь → части; логин и пароль отделяются от адреса по последнему «@» */
    private static UserInfo userInfo(String url) {
        String scheme = url.substring(0, "rtsp://".length());
        String rest = url.substring(scheme.length());
        int pathStart = indexOfPathAfterLastAt(rest);
        String authority = pathStart < 0 ? rest : rest.substring(0, pathStart);
        int at = authority.lastIndexOf('@');
        if (at < 0) return null;
        String credentials = authority.substring(0, at);
        int colon = credentials.indexOf(':');
        return new UserInfo(scheme,
                colon < 0 ? credentials : credentials.substring(0, colon),
                colon < 0 ? null : credentials.substring(colon + 1),
                rest.substring(at + 1));
    }

    /**
     * Начало пути после адреса. Ищется первый «/» после последнего «@»: пароль может содержать «/»,
     * а путь камеры символа «@» не содержит.
     */
    private static int indexOfPathAfterLastAt(String rest) {
        int at = rest.lastIndexOf('@');
        return rest.indexOf('/', Math.max(0, at));
    }

    /** Процентное кодирование всего, кроме незарезервированных символов RFC 3986 */
    private static String encode(String value) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '.' || c == '_' || c == '~';
            if (unreserved) {
                out.append(c);
            } else {
                out.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return out.toString();
    }

    /** Раскодирование только последовательностей %XX: «+» остаётся плюсом, в отличие от URLDecoder */
    private static String decode(String value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length() && isHex(value.charAt(i + 1)) && isHex(value.charAt(i + 2))) {
                bytes.write(Integer.parseInt(value.substring(i + 1, i + 3), 16));
                i += 3;
            } else {
                byte[] raw = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                bytes.write(raw, 0, raw.length);
                i += 1;
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
