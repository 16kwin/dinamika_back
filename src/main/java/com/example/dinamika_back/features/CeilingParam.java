package com.example.dinamika_back.features;

import com.example.dinamika_back.config.security.AppRoles;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Параметры безопасности (раздел 4.5 проекта). Это не выключатели, но они входят в белый список потолка:
 * задаются только в файле потолка (или берутся из умолчаний jar), входят в отпечаток и показываются в отчёте
 * с источником. Переопределение окружением, аргументами или системными свойствами отменяет запуск.
 *
 * <p>Умолчания заданы здесь, а не в {@code application.properties} (кроме уже лежащих там {@code server.*},
 * {@code sync.service.url}, {@code video.ffmpeg-path}). {@code video.ffprobe-path} — сверх списка 4.5 (см. ниже). Пакеты-владельцы берут значения из снимка:
 * {@code features.ceiling().param(CeilingParam.VIDEO_RETENTION_DAYS)}.
 */
public enum CeilingParam {

    SERVER_ADDRESS("server.address", "", Type.TEXT),
    SERVER_PORT("server.port", "8080", Type.PORT),
    SERVER_SSL_ENABLED("server.ssl.enabled", "false", Type.BOOL),
    SERVER_SSL_KEY_STORE("server.ssl.key-store", "", Type.TEXT),
    SERVER_SSL_PROTOCOL("server.ssl.protocol", "TLS", Type.TEXT),
    /** Адрес внешней системы заказов; без awms.network.outbound.allowed не используется (B3) */
    SYNC_SERVICE_URL("sync.service.url", "", Type.TEXT),
    /** Путь к ffmpeg; отчёт и check-surface печатают, есть ли файл по этому пути (B6) */
    VIDEO_FFMPEG_PATH("video.ffmpeg-path", "", Type.TEXT),
    /**
     * Путь к ffprobe; пусто — рядом с ffmpeg (VideoSettings). Сверх 4.5 проекта: это тоже запускаемый службой файл,
     * без белого списка VIDEO_FFPROBE_PATH подменял бы его мимо потолка и отпечатка
     */
    VIDEO_FFPROBE_PATH("video.ffprobe-path", "", Type.TEXT),
    /** Адреса фронтенда для CORS (B5); стенд добавляет свои адреса в потолке */
    CORS_ALLOWED_ORIGINS("awms.cors.allowed-origins", "http://localhost:3000,http://127.0.0.1:3000,http://localhost",
            Type.LIST),
    /** Адреса для WebSocket (B3); пусто — те же, что у CORS */
    WEBSOCKET_ALLOWED_ORIGINS("awms.websocket.allowed-origins", null, Type.LIST),
    /** Разрешённые исходящие host:port (B3); пусто — исходящих HTTP нет даже при включённых заказах */
    NETWORK_OUTBOUND_ALLOWED("awms.network.outbound.allowed", "", Type.LIST),
    /** Источники file: и test: у камер (B2) */
    VIDEO_ALLOW_FILE_SOURCE("awms.video.allow-file-source", "false", Type.BOOL),
    /** Сети камер (B2): проверяются по разрешённому IP в момент запуска ffmpeg */
    VIDEO_ALLOWED_NETWORKS("awms.video.allowed-networks", "10.0.0.0/8,172.16.0.0/12,192.168.0.0/16", Type.LIST),
    /** Срок хранения видео, дней (B11): действует при любом состоянии video, «бессрочно» не допускается */
    VIDEO_RETENTION_DAYS("awms.video.retention-days", "30", Type.INT, 1, 3650),
    /** Стереть пароли камер при старте с выключенным video (B11) */
    VIDEO_PURGE_CREDENTIALS("awms.video.purge-credentials", "false", Type.BOOL),
    /** Режим переключателей администратора (этап 2, B7): off | disable-only | full */
    ADMIN_MODE("awms.features.admin.mode", "disable-only", Type.ADMIN_MODE),
    /** Кто выключает переключателями */
    ADMIN_DISABLE_ROLES("awms.features.admin.disable-roles", "ADMIN,SECURITY", Type.ROLES),
    /** Кто, кроме SECURITY, включает обратно в режиме full; full без этого ключа отменяет запуск */
    ADMIN_ROLES("awms.features.admin.roles", "", Type.ROLES),
    /** Автоблокировка интерфейса, минут без действий (F2a) */
    UI_INACTIVITY_MINUTES("awms.ui.inactivity-minutes", "10", Type.INT, 1, 1440),
    /** За сколько минут до блокировки предупреждать */
    UI_WARNING_MINUTES("awms.ui.warning-minutes", "1", Type.INT, 0, 60);

    /** Тип значения: проверка и каноническая форма для отпечатка */
    enum Type { TEXT, BOOL, PORT, INT, LIST, ROLES, ADMIN_MODE }

    private static final Pattern ROLE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Set<String> ADMIN_MODES = Set.of("off", "disable-only", "full");

    private final String key;
    private final String defaultValue;
    private final Type type;
    private final int min;
    private final int max;
    private final ConfigurationPropertyName name;

    CeilingParam(String key, String defaultValue, Type type) {
        this(key, defaultValue, type, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    CeilingParam(String key, String defaultValue, Type type, int min, int max) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.type = type;
        this.min = min;
        this.max = max;
        this.name = ConfigurationPropertyName.of(key);
    }

    /** Имя свойства: {@code awms.video.retention-days} */
    public String key() {
        return key;
    }

    ConfigurationPropertyName propertyName() {
        return name;
    }

    /** Умолчание кода; для websocket-адресов — null (берутся адреса CORS) */
    String defaultValue() {
        return defaultValue;
    }

    /** Параметр по имени свойства (с учётом правил Spring: регистр, дефисы) или null */
    static CeilingParam byName(ConfigurationPropertyName name) {
        for (CeilingParam p : values()) {
            if (p.name.equals(name)) return p;
        }
        return null;
    }

    /** Проверка и каноническая форма значения; ошибка — {@link FeatureConfigurationException} с именем ключа */
    String normalize(String raw, String origin) {
        String value = raw == null ? "" : raw.trim();
        switch (type) {
            case TEXT:
                return value;
            case BOOL: {
                String v = value.toLowerCase(Locale.ROOT);
                if (!v.equals("true") && !v.equals("false")) throw invalid(raw, origin, "ожидается true или false");
                return v;
            }
            case PORT:
            case INT: {
                int lo = type == Type.PORT ? 0 : min;
                int hi = type == Type.PORT ? 65535 : max;
                int n;
                try {
                    n = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw invalid(raw, origin, "ожидается целое число от " + lo + " до " + hi);
                }
                if (n < lo || n > hi) throw invalid(raw, origin, "ожидается целое число от " + lo + " до " + hi);
                return Integer.toString(n);
            }
            case LIST: {
                Set<String> items = new TreeSet<>();
                for (String item : value.split(",")) {
                    String t = item.trim();
                    if (!t.isEmpty()) items.add(t);
                }
                return String.join(",", items);
            }
            case ROLES: {
                Set<String> roles = AppRoles.parse(value);
                for (String role : roles) {
                    if (!ROLE.matcher(role).matches()) throw invalid(raw, origin, "некорректное имя роли «" + role + "»");
                }
                return String.join(",", roles);
            }
            case ADMIN_MODE: {
                String v = value.toLowerCase(Locale.ROOT);
                if (!ADMIN_MODES.contains(v)) throw invalid(raw, origin, "ожидается off, disable-only или full");
                return v;
            }
            default:
                throw new IllegalStateException(type.name());
        }
    }

    /** Список из канонической формы значения */
    static List<String> splitList(String value) {
        if (value == null || value.isEmpty()) return List.of();
        return List.of(value.split(","));
    }

    private FeatureConfigurationException invalid(String raw, String origin, String expected) {
        return new FeatureConfigurationException("Параметр потолка " + key + "=«" + raw + "» (" + origin + "): "
                + expected);
    }
}
