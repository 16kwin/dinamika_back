package com.example.dinamika_back.features.report;

import com.example.dinamika_back.config.security.AppRoles;
import com.example.dinamika_back.config.security.PublicEndpoints;
import com.example.dinamika_back.features.CeilingParam;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureLevel;
import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.features.FeatureSnapshot;
import com.example.dinamika_back.features.FeatureState;
import com.example.dinamika_back.features.GateRule;
import com.example.dinamika_back.features.audit.AccessLogRepository;
import com.example.dinamika_back.features.journal.FeatureJournal;
import com.example.dinamika_back.features.journal.FeatureLogEntry;
import com.example.dinamika_back.features.journal.StartupProfileAuditor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Отчёт «Профиль безопасности» (раздел 9 проекта): строится из реестра функций, потолка, журнала и живого состояния
 * приложения, а не пишется вручную. Состав отчёта постоянный: одни и те же разделы и поля в одном порядке (меняются
 * только значения), поэтому печать и выгрузку JSON можно сверять между собой.
 *
 * <p>В отчёт не попадают значения секретов, пароли и адреса камер с учётными данными: у секретов — только источник
 * («файл секретов установки», «значение из репозитория»), у камер — хосты. Запросы к БД — только по константным
 * именам таблиц из реестра; сбой запроса даёт пустое значение раздела, а не ошибку отчёта.
 */
@Service
public class SecurityProfileService {

    /** Сводка журнала доступа — за 30 дней (раздел 9.2, строка 13) */
    static final int USAGE_DAYS = 30;
    /** Последних записей журнала профиля в отчёте */
    static final int JOURNAL_ENTRIES = 50;
    /** Типы журнала профиля, которые отчёт показывает всегда (раздел 9.2, строка 9) */
    static final List<FeatureLogEntry.Type> NOTABLE = List.of(FeatureLogEntry.Type.TAMPER, FeatureLogEntry.Type.PURGE,
            FeatureLogEntry.Type.RETENTION, FeatureLogEntry.Type.CREDENTIALS_PURGED, FeatureLogEntry.Type.CEILING_CHANGED);
    /** Секреты установки: в отчёте только источник значения (B10 переносит их в файл секретов) */
    static final List<String> SECRET_KEYS = List.of("jwt.cookie-token-key", "spring.datasource.password",
            "spring.flyway.password", "api.key.awms", "awms.journal.hmac-key", "awms.video.credentials-key");
    /** Ключ токенов, пароль БД и ключ API из jar — значения из репозитория (раздел 2, п. 10) */
    private static final Set<String> REPOSITORY_CRITICAL = Set.of("jwt.cookie-token-key", "spring.datasource.password",
            "api.key.awms");
    /** Бин пакета B10, после которого роль берётся из БД */
    static final String DB_ROLE_MARKER = "com.example.dinamika_back.features.secrets.DbRoleCache";
    /** Задача срока хранения видео (пакет B11) */
    static final String RETENTION_MARKER = "com.example.dinamika_back.features.data.VideoRetentionJob";
    static final String PURGE_PATH = "/api/security-profile/purge";
    private static final Pattern TABLE = Pattern.compile("[a-z_][a-z0-9_]{0,62}");
    /** Предупреждение разбора потолка о функции, выключенной зависимостью (FeatureSnapshot.cascade) */
    private static final Pattern CASCADE_WARNING = Pattern.compile("Функция (\\S+) выключена: выключена функция \\S+, от которой она зависит");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final FeatureService features;
    private final FeatureJournal journal;
    private final ObjectProvider<StartupProfileAuditor> auditor;
    private final ObjectProvider<AccessLogRepository> accessLog;
    private final RuntimeInspector runtime;
    private final ObjectProvider<JdbcTemplate> jdbc;
    private final ObjectProvider<BuildProperties> build;
    private final Environment environment;
    private final Clock clock;
    private final String hostName;

    @Autowired
    public SecurityProfileService(FeatureService features, FeatureJournal journal,
                                  ObjectProvider<StartupProfileAuditor> auditor,
                                  ObjectProvider<AccessLogRepository> accessLog, RuntimeInspector runtime,
                                  ObjectProvider<JdbcTemplate> jdbc, ObjectProvider<BuildProperties> build,
                                  Environment environment) {
        this(features, journal, auditor, accessLog, runtime, jdbc, build, environment, Clock.systemDefaultZone(),
                FeatureJournal.hostName());
    }

    /** Для тестов: свои часы и имя компьютера */
    public SecurityProfileService(FeatureService features, FeatureJournal journal,
                                  ObjectProvider<StartupProfileAuditor> auditor,
                                  ObjectProvider<AccessLogRepository> accessLog, RuntimeInspector runtime,
                                  ObjectProvider<JdbcTemplate> jdbc, ObjectProvider<BuildProperties> build,
                                  Environment environment, Clock clock, String hostName) {
        this.features = features;
        this.journal = journal;
        this.auditor = auditor;
        this.accessLog = accessLog;
        this.runtime = runtime;
        this.jdbc = jdbc;
        this.build = build;
        this.environment = environment;
        this.clock = clock;
        this.hostName = hostName;
    }

    /** Полный отчёт для просматривающего (его логин и роль — в разделе viewer) */
    public Map<String, Object> report(Authentication viewer) {
        FeatureSnapshot snapshot = features.snapshot();
        FeatureSnapshot ceiling = features.ceiling();
        List<RuntimeInspector.Route> routes = runtime.routes();
        boolean purgeAvailable = routes.stream().anyMatch(r -> r.pattern().equals(PURGE_PATH)
                && (r.methods().isEmpty() || r.methods().contains("POST")));
        String roleSource = runtime.beanPresent(DB_ROLE_MARKER) ? "db" : "token";
        Integer port = runtime.port();
        FeatureJournal.Verification chain = journal.verify();
        Map<String, Object> dbUser = dbUser();
        List<String> warnings = new ArrayList<>();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", Instant.now(clock).toString());
        report.put("viewer", viewer(viewer, purgeAvailable));
        report.put("header", header(snapshot, ceiling, chain, roleSource, dbUser, warnings));
        report.put("features", features(snapshot, roleSource));
        report.put("params", params(ceiling));
        report.put("absent", absent());
        report.put("runtime", runtime.runtimeChecks().stream().map(c -> check(c.text(), c.ok())).toList());
        report.put("inbound", inbound(snapshot, ceiling, routes, port));
        report.put("outbound", outbound(ceiling));
        report.put("background", background(ceiling));
        report.put("storedData", storedData(snapshot, ceiling, purgeAvailable));
        report.put("components", components());
        report.put("journal", journalSection(chain));
        report.put("selfCheck", selfCheck(snapshot, ceiling, port));
        report.put("roles", roles(ceiling, roleSource));
        report.put("usage", usage());
        report.put("caveats", caveats(snapshot, ceiling, roleSource, dbUser));
        report.put("probes", probes(snapshot));
        return report;
    }

    // ===================== 0. Кто смотрит =====================

    private static Map<String, Object> viewer(Authentication viewer, boolean purgeAvailable) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("login", viewer == null ? null : viewer.getName());
        result.put("role", viewer == null ? null : viewer.getAuthorities().stream()
                .map(a -> AppRoles.roleName(a.getAuthority())).filter(r -> !r.isEmpty()).sorted().findFirst().orElse(null));
        // Удаление данных выключенной функции — только SECURITY и только когда команда есть (пакет B11)
        result.put("canPurge", purgeAvailable && AppRoles.isSecurity(viewer));
        return result;
    }

    // ===================== 1. Шапка =====================

    private Map<String, Object> header(FeatureSnapshot snapshot, FeatureSnapshot ceiling,
                                       FeatureJournal.Verification chain, String roleSource,
                                       Map<String, Object> dbUser, List<String> warnings) {
        Map<String, Object> h = new LinkedHashMap<>();
        BuildProperties info = build.getIfAvailable();
        Map<String, Object> backend = new LinkedHashMap<>();
        backend.put("version", info == null ? null : info.getVersion());
        backend.put("buildTime", info == null || info.getTime() == null ? null : info.getTime().toString());
        backend.put("artifact", info == null ? null : info.getArtifact());
        h.put("backend", backend);
        h.put("host", hostName);
        h.put("preset", ceiling.preset().key());
        h.put("presetSource", ceiling.presetSource());
        h.put("fingerprint", ceiling.fingerprint());
        h.put("revision", snapshot.revision());
        h.put("ceilingFile", ceilingFile(ceiling));
        h.put("ticket", ceiling.ticket());
        h.put("adminMode", ceiling.param(CeilingParam.ADMIN_MODE));
        h.put("journalHead", journal.head().map(SecurityProfileService::head).orElse(null));
        h.put("secrets", secrets());
        h.put("roleSource", roleSource);
        h.put("dbUser", dbUser);

        // Функции, выключенные вслед за родителем или requires, — одной строкой: по строке на каждую заслоняли бы
        // настоящие предупреждения (подробности — в разделе «Функции»)
        List<String> cascade = new ArrayList<>();
        for (String warning : ceiling.warnings()) {
            Matcher m = CASCADE_WARNING.matcher(warning);
            if (m.matches()) cascade.add(m.group(1));
            else warnings.add(warning);
        }
        if (!cascade.isEmpty()) {
            warnings.add("Выключены вслед за функцией, от которой зависят: " + String.join(", ", cascade));
        }
        if ("env".equals(ceiling.presetSource())) {
            warnings.add("Профиль задан переменной окружения AWMS_FEATURES_PRESET без файла потолка (режим "
                    + "разработчика) — для согласования непригоден");
        }
        if (!chain.valid()) {
            warnings.add("Журнал профиля: цепочка нарушена на записи " + chain.brokenAt() + " — " + chain.problem());
        }
        StartupProfileAuditor.Status status = auditorStatus();
        if (status != null && status.head() != null && status.head().mismatch()) {
            warnings.add("Журнал профиля: " + status.head().text());
        }
        if (Boolean.TRUE.equals(dbUser.get("superuser"))) {
            warnings.add("Приложение работает под суперпользователем БД (" + dbUser.get("name") + "): журналы и "
                    + "переключатели может править администратор БД. Обязательный шаг установки — рабочая роль БД "
                    + "(deploy/db/roles.sql)");
        }
        h.put("warnings", List.copyOf(warnings));
        return h;
    }

    private static Map<String, Object> head(FeatureJournal.Head head) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", head.id());
        result.put("hash", head.hash());
        return result;
    }

    private static Map<String, Object> ceilingFile(FeatureSnapshot ceiling) {
        Map<String, Object> file = new LinkedHashMap<>();
        Path path = ceiling.ceilingFile();
        file.put("path", path == null ? null : path.toString());
        file.put("exists", ceiling.ceilingFileExists());
        file.put("sha256", ceiling.ceilingFileSha256());
        String modified = null;
        String owner = null;
        if (path != null && ceiling.ceilingFileExists()) {
            try {
                modified = LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), ZONE).withNano(0).toString();
            } catch (IOException | RuntimeException e) {
                modified = null;
            }
            try {
                owner = Files.getOwner(path).getName();
            } catch (IOException | RuntimeException e) {
                owner = null;
            }
        }
        file.put("modifiedAt", modified);
        file.put("owner", owner);
        return file;
    }

    /** Состояние секретов: только источник значения, сами значения в отчёт не попадают */
    Map<String, Object> secrets() {
        List<Map<String, Object>> items = new ArrayList<>();
        boolean repository = false;
        boolean allInstallation = true;
        for (String key : SECRET_KEYS) {
            String source = secretSource(key);
            if (REPOSITORY_CRITICAL.contains(key) && "repository".equals(source)) repository = true;
            if (!"installation".equals(source)) allInstallation = false;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("source", source);
            item.put("text", switch (source) {
                case "repository" -> "значение из репозитория (application.properties в jar)";
                case "installation" -> "файл секретов установки";
                case "environment" -> "переменная окружения";
                case "absent" -> "не задан";
                default -> "другой источник настроек";
            });
            items.add(item);
        }
        String state;
        String text;
        if (repository) {
            state = "repository";
            text = "совпадают с репозиторием: ключ токенов, пароль БД и ключ API взяты из application.properties в jar. "
                    + "Свои секреты установки создаёт команда init-secrets (пакет B10)";
        } else if (allInstallation) {
            state = "generated";
            text = "сгенерированы при установке (файл секретов)";
        } else {
            state = "partial";
            text = "часть секретов не задана или задана вне файла секретов установки";
        }
        if (!journal.keyed()) text += "; ключа HMAC журнала нет — цепочка журнала без ключа";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", state);
        result.put("text", text);
        result.put("items", items);
        return result;
    }

    private String secretSource(String key) {
        if (!(environment instanceof ConfigurableEnvironment configurable)) {
            return environment.getProperty(key) == null ? "absent" : "other";
        }
        for (PropertySource<?> ps : configurable.getPropertySources()) {
            if (ConfigurationPropertySources.isAttachedConfigurationPropertySource(ps)) continue;
            Object value = ps.getProperty(key);
            if (value == null || String.valueOf(value).isBlank()) continue;
            String name = ps.getName();
            if (name.contains("class path resource")) return "repository";
            if (name.contains("awms-secrets")) return "installation";
            if (ps instanceof SystemEnvironmentPropertySource) return "environment";
            return "other";
        }
        return "absent";
    }

    private Map<String, Object> dbUser() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> row = query(t -> t.queryForMap("SELECT current_user AS name, "
                + "(SELECT rolsuper FROM pg_roles WHERE rolname = current_user) AS superuser"));
        result.put("name", row == null ? null : row.get("name"));
        result.put("superuser", row == null ? null : row.get("superuser"));
        return result;
    }

    // ===================== 2. Функции =====================

    private List<Map<String, Object>> features(FeatureSnapshot snapshot, String roleSource) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Feature f : Feature.real()) {
            FeatureState state = snapshot.state(f);
            FeatureLevel level = actualLevel(f, snapshot);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", f.key());
            row.put("title", f.title());
            row.put("enabled", state.enabled());
            row.put("reason", state.reasonCode());
            row.put("reasonText", state.reasonText());
            row.put("level", level.name());
            row.put("levelText", levelText(f, level));
            row.put("origin", state.origin());
            row.put("roles", List.copyOf(state.roles()));
            row.put("rolesGuaranteed", state.roles().isEmpty() ? null : "db".equals(roleSource));
            row.put("probe", f.probe());
            row.put("checks", checks(f, snapshot));
            rows.add(row);
        }
        return rows;
    }

    /**
     * Фактический уровень: у включённой функции — уровень, которого она достигает при выключении потолком;
     * у выключенной — что на деле: модуль не загружен (L3), маршрут закрыт воротами при живых бинах (L2), только
     * интерфейс (L1), нет в сборке (L4)
     */
    FeatureLevel actualLevel(Feature f, FeatureSnapshot snapshot) {
        FeatureState state = snapshot.state(f);
        if (state.reason() == FeatureState.Reason.ABSENT_IN_BUILD) return FeatureLevel.L4;
        if (state.enabled()) return f.level();
        if (state.reason() == FeatureState.Reason.SWITCH || state.reason() == FeatureState.Reason.TAMPER) {
            return f.isUiOnly() ? FeatureLevel.L1 : FeatureLevel.L2;
        }
        // Модуль функции или её предка не загружен — бинов нет
        for (Feature a = f; a != null; a = a.parent()) {
            if (!snapshot.isOn(a) && a.marker() != null && !runtime.beanPresent(a.marker())) return FeatureLevel.L3;
        }
        // Скачивание при разрешённом просмотре — L1 (раздел 3.1): /content отдаёт тот же файл
        if (f == Feature.VIDEO_DOWNLOAD) return snapshot.isOn(Feature.VIDEO_ARCHIVE) ? FeatureLevel.L1 : FeatureLevel.L2;
        if (f.level() == FeatureLevel.L3 && f.marker() != null && runtime.beanPresent(f.marker())) return FeatureLevel.L2;
        return f.level();
    }

    private static String levelText(Feature f, FeatureLevel level) {
        String text = level.title();
        if (level == FeatureLevel.L1 && (f == Feature.VIDEO_DOWNLOAD || f == Feature.EXPORT)) {
            text += ": убраны кнопки и серверный рендер; данные, доступные для просмотра, клиент сохранить может";
        }
        if (level == FeatureLevel.L2 && f == Feature.GPP) text += " (бины ГПП остаются до пакета B8)";
        return text;
    }

    /** Самопроверки выключенной функции: бина нет, потоков нет, дочерних процессов нет, маршруты закрыты */
    private List<Map<String, Object>> checks(Feature f, FeatureSnapshot snapshot) {
        List<Map<String, Object>> checks = new ArrayList<>();
        if (snapshot.isOn(f)) return checks;
        if (f.marker() != null) {
            String bean = f.marker().substring(f.marker().lastIndexOf('.') + 1);
            boolean present = runtime.beanPresent(f.marker());
            checks.add(present
                    ? check("бин " + bean + " есть: маршруты закрыты воротами (L2)", null)
                    : check("бин " + bean + " отсутствует", true));
        }
        if (!f.threads().isEmpty()) {
            int count = 0;
            for (String prefix : f.threads()) count += runtime.threadsWithPrefix(prefix);
            String names = String.join(", ", f.threads().stream().map(p -> p + "*").toList());
            checks.add(count == 0 ? check("потоков " + names + " нет", true)
                    : check("потоков " + names + ": " + count + " — ожидалось 0", false));
        }
        if (f == Feature.VIDEO) {
            long ffmpeg = runtime.childProcesses().stream()
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith("ffmpeg") || n.toLowerCase(Locale.ROOT).startsWith("ffprobe"))
                    .count();
            checks.add(ffmpeg == 0 ? check("дочерних процессов ffmpeg и ffprobe нет", true)
                    : check("дочерних процессов ffmpeg/ffprobe: " + ffmpeg + " — ожидалось 0", false));
        }
        long gates = f.rules().stream().filter(r -> r.kind() != GateRule.Kind.MIXED).count();
        if (gates > 0) {
            checks.add(check("ворота закрывают правил: " + gates + " (ответ 404 на все методы правила, GET — и HEAD)", true));
        }
        if (f == Feature.ORDERS || f == Feature.TESTSYNC) {
            int templates = runtime.beanCount(RestTemplate.class);
            if (!snapshot.isOn(Feature.ORDERS) && !snapshot.isOn(Feature.TESTSYNC)) {
                checks.add(templates == 0 ? check("бинов RestTemplate нет", true)
                        : check("бинов RestTemplate: " + templates + " — ожидалось 0", false));
            }
        }
        return checks;
    }

    // ===================== Параметры и отсутствующее =====================

    private static List<Map<String, Object>> params(FeatureSnapshot ceiling) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CeilingParam p : CeilingParam.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", p.key());
            row.put("value", ceiling.param(p));
            row.put("origin", ceiling.paramOrigin(p));
            row.put("title", PARAM_TITLES.get(p));
            rows.add(row);
        }
        return rows;
    }

    private static final Map<CeilingParam, String> PARAM_TITLES = paramTitles();

    private static Map<CeilingParam, String> paramTitles() {
        Map<CeilingParam, String> t = new LinkedHashMap<>();
        t.put(CeilingParam.SERVER_ADDRESS, "Адрес, на котором слушает сервер (пусто — все интерфейсы)");
        t.put(CeilingParam.SERVER_PORT, "Порт приложения");
        t.put(CeilingParam.SERVER_SSL_ENABLED, "TLS в самом приложении");
        t.put(CeilingParam.SERVER_SSL_KEY_STORE, "Хранилище ключей TLS");
        t.put(CeilingParam.SERVER_SSL_PROTOCOL, "Протокол TLS");
        t.put(CeilingParam.SYNC_SERVICE_URL, "Адрес внешней системы заказов (без разрешённых исходящих не используется)");
        t.put(CeilingParam.VIDEO_FFMPEG_PATH, "Путь к ffmpeg");
        t.put(CeilingParam.VIDEO_FFPROBE_PATH, "Путь к ffprobe (пусто — рядом с ffmpeg)");
        t.put(CeilingParam.CORS_ALLOWED_ORIGINS, "Адреса фронтенда для CORS");
        t.put(CeilingParam.WEBSOCKET_ALLOWED_ORIGINS, "Адреса для WebSocket");
        t.put(CeilingParam.NETWORK_OUTBOUND_ALLOWED, "Разрешённые исходящие соединения (хост:порт)");
        t.put(CeilingParam.VIDEO_ALLOW_FILE_SOURCE, "Источники file: и test: у камер");
        t.put(CeilingParam.VIDEO_ALLOWED_NETWORKS, "Сети, в которых допустимы камеры");
        t.put(CeilingParam.VIDEO_RETENTION_DAYS, "Срок хранения видео, дней");
        t.put(CeilingParam.VIDEO_PURGE_CREDENTIALS, "Стирать пароли камер при выключенном видео");
        t.put(CeilingParam.ADMIN_MODE, "Режим переключателей администратора");
        t.put(CeilingParam.ADMIN_DISABLE_ROLES, "Кто выключает функции переключателями");
        t.put(CeilingParam.ADMIN_ROLES, "Кто, кроме SECURITY, включает обратно (режим full)");
        t.put(CeilingParam.UI_INACTIVITY_MINUTES, "Автоблокировка интерфейса, минут без действий");
        t.put(CeilingParam.UI_WARNING_MINUTES, "Предупреждение до блокировки, минут");
        return t;
    }

    private static List<Map<String, Object>> absent() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Feature f : Feature.values()) {
            if (!f.isAbsent()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", f.key());
            row.put("title", f.title());
            row.put("reason", f.absentReason());
            rows.add(row);
        }
        return rows;
    }

    // ===================== 4. Входящие =====================

    private Map<String, Object> inbound(FeatureSnapshot snapshot, FeatureSnapshot ceiling,
                                        List<RuntimeInspector.Route> routes, Integer port) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("port", port);
        section.put("address", ceiling.param(CeilingParam.SERVER_ADDRESS));
        section.put("tls", ceiling.boolParam(CeilingParam.SERVER_SSL_ENABLED));
        section.put("websocket", Feature.WEBSOCKET.marker() != null && runtime.beanPresent(Feature.WEBSOCKET.marker()));
        Map<String, Integer> byPrefix = new TreeMap<>();
        for (RuntimeInspector.Route r : routes) byPrefix.merge(RuntimeInspector.routePrefix(r.pattern()), 1, Integer::sum);
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("total", routes.size());
        table.put("byPrefix", byPrefix.entrySet().stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("prefix", e.getKey());
            row.put("count", e.getValue());
            return row;
        }).toList());
        section.put("routes", table);
        section.put("gated", gated(snapshot, routes));
        section.put("anonymous", PublicEndpoints.withoutSignIn(ceiling).stream().map(PublicEndpoints.Endpoint::toString).toList());
        return section;
    }

    /** Правила выключенных функций, под которые попадают живые маршруты: бины есть, ответ — 404 воротами (L2) */
    private static List<Map<String, Object>> gated(FeatureSnapshot snapshot, List<RuntimeInspector.Route> routes) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (FeatureSnapshot.ClosedRule closed : snapshot.closedRules()) {
            GateRule rule = closed.rule();
            long matched = routes.stream().filter(r -> rule.matchesPath(RuntimeInspector.samplePath(r.pattern()))
                    && (r.methods().isEmpty() || rule.method() == null || r.methods().stream().anyMatch(rule::matchesMethod)))
                    .count();
            if (matched == 0) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("feature", closed.feature().key());
            row.put("rule", rule.toString());
            row.put("routes", matched);
            result.add(row);
        }
        return result;
    }

    // ===================== 5. Исходящие =====================

    private Map<String, Object> outbound(FeatureSnapshot ceiling) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("database", databaseAddress(environment.getProperty("spring.datasource.url")));
        List<String> allowed = ceiling.listParam(CeilingParam.NETWORK_OUTBOUND_ALLOWED);
        boolean syncUsed = (ceiling.isOn(Feature.ORDERS) || ceiling.isOn(Feature.TESTSYNC)) && !allowed.isEmpty();
        section.put("syncService", syncUsed ? withoutCredentials(ceiling.param(CeilingParam.SYNC_SERVICE_URL)) : null);
        section.put("outboundAllowed", allowed);
        section.put("restTemplates", runtime.beanCount(RestTemplate.class));
        section.put("cameras", cameras(ceiling));
        return section;
    }

    /** Камеры: число, хосты (без учётных данных), сколько с сохранённым паролем; сами адреса не отдаются */
    private Map<String, Object> cameras(FeatureSnapshot ceiling) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> urls = query(t -> t.queryForList("SELECT rtsp_url FROM video_camera ORDER BY id", String.class));
        Long encrypted = rows("video_camera_secret");
        boolean polled = ceiling.isOn(Feature.VIDEO);
        int plain = 0;
        Set<String> hosts = new TreeSet<>();
        if (urls != null) {
            for (String url : urls) {
                try {
                    URI uri = URI.create(url == null ? "" : url.trim());
                    String userInfo = uri.getRawUserInfo();
                    if (userInfo != null && userInfo.indexOf(':') >= 0 && userInfo.indexOf(':') < userInfo.length() - 1) plain++;
                    if (uri.getHost() != null) hosts.add(uri.getHost());
                } catch (IllegalArgumentException e) {
                    // Адрес не разбирается — не считаем ни хостом, ни паролем
                }
            }
        }
        result.put("count", urls == null ? null : urls.size());
        result.put("polled", polled);
        result.put("hosts", polled ? List.copyOf(hosts) : List.of());
        long secretCount = encrypted == null ? 0 : encrypted;
        result.put("withPassword", urls == null ? null : plain + secretCount);
        result.put("encrypted", urls == null || plain + secretCount == 0 ? null : plain == 0);
        // До пакета B11 пароль камеры передаётся ffmpeg в адресе -i (аргументы процесса)
        result.put("passwordInArgs", urls == null ? null : plain > 0);
        return result;
    }

    static String databaseAddress(String url) {
        if (url == null) return null;
        String rest = url.startsWith("jdbc:") ? url.substring(5) : url;
        try {
            URI uri = URI.create(rest);
            String host = uri.getHost();
            if (host == null) return null;
            String db = uri.getPath() == null ? "" : uri.getPath();
            return host + ":" + (uri.getPort() < 0 ? 5432 : uri.getPort()) + db;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String withoutCredentials(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI uri = URI.create(url.trim());
            if (uri.getHost() == null) return null;
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort())
                    + (uri.getRawPath() == null ? "" : uri.getRawPath());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ===================== 6. Фон =====================

    private Map<String, Object> background(FeatureSnapshot ceiling) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("threads", runtime.threadsByPrefix().entrySet().stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("prefix", e.getKey());
            row.put("count", e.getValue());
            return row;
        }).toList());
        section.put("scheduled", runtime.scheduledTasks());
        section.put("childProcesses", runtime.childProcesses());
        section.put("startupTasks", startupTasks(ceiling));
        Map<String, Object> ffmpeg = new LinkedHashMap<>();
        String path = ceiling.param(CeilingParam.VIDEO_FFMPEG_PATH);
        ffmpeg.put("path", path == null || path.isEmpty() ? null : path);
        ffmpeg.put("exists", path == null || path.isEmpty() ? null : Files.isRegularFile(Paths.get(path)));
        section.put("ffmpeg", ffmpeg);
        return section;
    }

    private List<String> startupTasks(FeatureSnapshot ceiling) {
        List<String> tasks = new ArrayList<>();
        tasks.add("Аудит профиля и журнала (журнал профиля: STARTUP, CEILING_CHANGED): при каждом старте");
        tasks.add("Сводка отказов ворот в журнал профиля (поток feature-denied-summary): раз в час");
        tasks.add("Импорт migration_photos (photoimport): " + (runtime.beanPresent(Feature.PHOTOIMPORT.marker())
                ? "выполняется при старте" : "бина нет — не выполняется"));
        if (runtime.beanPresent(Feature.MAP_PLAN.marker())) {
            tasks.add("Чистка файлов-сирот планов цехов (map.plan): "
                    + ("true".equalsIgnoreCase(environment.getProperty("station-plans.orphan-cleanup"))
                    ? "включена" : "выключена настройкой station-plans.orphan-cleanup"));
        } else {
            tasks.add("Чистка файлов-сирот планов цехов (map.plan): бина нет — не выполняется");
        }
        tasks.add("Закрытие прерванных записей видео (video): " + (runtime.beanPresent(Feature.VIDEO.marker())
                ? "выполняется при старте" : "бина нет — не выполняется"));
        tasks.add("Срок хранения видео " + ceiling.param(CeilingParam.VIDEO_RETENTION_DAYS) + " дн. (VideoRetentionJob): "
                + (runtime.beanPresent(RETENTION_MARKER) ? "работает при любом состоянии video"
                : "задача пакета B11 ещё не подключена — записи по сроку не удаляются"));
        return tasks;
    }

    // ===================== 7. Хранимые данные выключенных функций =====================

    private List<Map<String, Object>> storedData(FeatureSnapshot snapshot, FeatureSnapshot ceiling, boolean purgeAvailable) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Feature f : Feature.real()) {
            if (snapshot.isOn(f) || (f.tables().isEmpty() && f.dirProperties().isEmpty())) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("feature", f.key());
            row.put("title", f.title());
            List<Map<String, Object>> tables = new ArrayList<>();
            for (String table : f.tables()) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("name", table);
                t.put("rows", rows(table));
                tables.add(t);
            }
            row.put("tables", tables);
            List<Map<String, Object>> dirs = new ArrayList<>();
            for (String property : f.dirProperties()) dirs.add(directory(property));
            row.put("dirs", dirs);
            if (f == Feature.VIDEO) {
                Timestamp oldest = query(t -> t.queryForObject("SELECT min(started_at) FROM video_record", Timestamp.class));
                row.put("oldest", oldest == null ? null : oldest.toLocalDateTime().withNano(0).toString());
                row.put("retentionDays", ceiling.intParam(CeilingParam.VIDEO_RETENTION_DAYS));
            } else {
                row.put("oldest", null);
                row.put("retentionDays", null);
            }
            row.put("note", "хранится, доступ закрыт");
            // Удалять можно только данные функции, выключенной потолком (не переключателем), и только командой B11
            row.put("purgeAllowed", purgeAvailable && !ceiling.isOn(f));
            result.add(row);
        }
        return result;
    }

    private Map<String, Object> directory(String property) {
        Map<String, Object> dir = new LinkedHashMap<>();
        String value = environment.getProperty(property);
        Path path = value == null || value.isBlank() ? null : Paths.get(value.trim()).toAbsolutePath().normalize();
        dir.put("property", property);
        dir.put("path", path == null ? null : path.toString());
        long[] totals = path == null ? null : walk(path);
        dir.put("files", totals == null ? null : totals[0]);
        dir.put("bytes", totals == null ? null : totals[1]);
        return dir;
    }

    /** Число файлов и байт в каталоге; каталога нет — null */
    private static long[] walk(Path dir) {
        if (!Files.isDirectory(dir)) return null;
        long[] totals = new long[2];
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        totals[0]++;
                        totals[1] += attrs.size();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return totals;
        }
        return totals;
    }

    /** Строк в таблице реестра; таблицы нет или БД недоступна — null */
    private Long rows(String table) {
        if (!TABLE.matcher(table).matches()) return null;
        return query(t -> {
            Boolean exists = t.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, "public." + table);
            if (!Boolean.TRUE.equals(exists)) return null;
            return t.queryForObject("SELECT count(*) FROM " + table, Long.class);
        });
    }

    // ===================== 8. Компоненты =====================

    private Map<String, Object> components() {
        Map<String, Object> section = new LinkedHashMap<>();
        boolean sbom = new ClassPathResource("META-INF/sbom/bom.json").exists();
        section.put("backendSbom", sbom ? "META-INF/sbom/bom.json (CycloneDX)" : null);
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("Spring Boot", SpringBootVersion.getVersion()));
        items.add(item("Java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")"));
        BuildProperties info = build.getIfAvailable();
        items.add(item("Сборка бэкенда (build-info)", info == null ? "не сформирована"
                : info.getGroup() + ":" + info.getArtifact() + ":" + info.getVersion()));
        items.add(item("Фронтенд", "состав — в /awms-build.json варианта сборки (показывается рядом)"));
        section.put("items", items);
        return section;
    }

    private static Map<String, Object> item(String title, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", title);
        row.put("value", value);
        return row;
    }

    // ===================== 9. Журнал профиля =====================

    private Map<String, Object> journalSection(FeatureJournal.Verification chain) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("chainValid", chain.valid());
        section.put("brokenAt", chain.brokenAt());
        section.put("problem", chain.problem());
        section.put("keyed", journal.keyed());
        section.put("keyId", journal.keyId());
        section.put("checked", chain.checked());
        section.put("total", journal.count());
        section.put("head", journal.head().map(SecurityProfileService::head).orElse(null));
        StartupProfileAuditor.Status status = auditorStatus();
        section.put("headCheck", status == null || status.head() == null ? null : status.head().text());
        Map<Long, FeatureLogEntry> entries = new TreeMap<>(Comparator.reverseOrder());
        for (FeatureLogEntry e : journal.recent(JOURNAL_ENTRIES)) entries.put(e.getId(), e);
        for (FeatureLogEntry e : journal.recent(NOTABLE, JOURNAL_ENTRIES)) entries.put(e.getId(), e);
        section.put("entries", entries.values().stream().map(SecurityProfileService::entry).toList());
        return section;
    }

    private static Map<String, Object> entry(FeatureLogEntry e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("occurredAt", e.getOccurredAt() == null ? null : e.getOccurredAt().toString());
        row.put("type", e.getEventType());
        row.put("featureKey", e.getFeatureKey());
        row.put("oldValue", e.getOldValue());
        row.put("newValue", e.getNewValue());
        row.put("actor", e.getActor());
        row.put("actorRole", e.getActorRole());
        row.put("reason", e.getReason());
        row.put("ticket", e.getTicket());
        row.put("hash", e.getRowHash());
        return row;
    }

    // ===================== 11. Как проверить самостоятельно =====================

    private List<Map<String, Object>> selfCheck(FeatureSnapshot snapshot, FeatureSnapshot ceiling, Integer port) {
        String server = "http://<сервер>:" + (port == null ? ceiling.param(CeilingParam.SERVER_PORT) : port);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> closedProbes = probes(snapshot).stream().map(p -> String.valueOf(p.get("url"))).toList();
        if (!closedProbes.isEmpty()) {
            rows.add(selfCheckRow("Закрытая функция отвечает 404 — анонимно и под администратором",
                    "curl -i " + server + closedProbes.get(0), "HTTP 404"));
            String head = closedProbes.stream().filter(u -> u.contains("/download")).findFirst().orElse(closedProbes.get(0));
            rows.add(selfCheckRow("Метод HEAD закрыт так же, как GET", "curl -I " + server + head, "HTTP 404"));
        }
        rows.add(selfCheckRow("Открытые порты сервера", "nmap -p- <сервер>",
                "один порт приложения (" + (port == null ? ceiling.param(CeilingParam.SERVER_PORT) : port)
                        + ") и порты веб-сервера"));
        boolean video = snapshot.isOn(Feature.VIDEO);
        rows.add(selfCheckRow("Процессы ffmpeg", "tasklist | findstr /i ffmpeg   (Linux: pgrep -a ffmpeg)",
                video ? "только во время живого просмотра и записи" : "пусто"));
        String ffmpeg = ceiling.param(CeilingParam.VIDEO_FFMPEG_PATH);
        if (ffmpeg != null && !ffmpeg.isEmpty()) {
            rows.add(selfCheckRow("Файл ffmpeg по video.ffmpeg-path", "dir \"" + ffmpeg + "\"   (Linux: ls -l " + ffmpeg + ")",
                    video ? "есть" : "нет: при выключенном видео ffmpeg не устанавливается"));
        }
        rows.add(selfCheckRow("Соединения с камерами (RTSP)", "netstat -ano | findstr :554   (Linux: ss -tn | grep :554)",
                video ? "только во время просмотра и записи" : "пусто"));
        String db = databaseAddress(environment.getProperty("spring.datasource.url"));
        rows.add(selfCheckRow("Запрет исходящих соединений", "запретить брандмауэром все исходящие с сервера, кроме "
                + (db == null ? "PostgreSQL" : "PostgreSQL " + db), "система работает"));
        if (ceiling.ceilingFile() != null) {
            rows.add(selfCheckRow("Файл потолка не изменён", "Get-FileHash \"" + ceiling.ceilingFile()
                    + "\"   (Linux: sha256sum " + ceiling.ceilingFile() + ")", ceiling.ceilingFileExists()
                    ? String.valueOf(ceiling.ceilingFileSha256()).toUpperCase(Locale.ROOT) : "файла нет — пресет isolated"));
            rows.add(selfCheckRow("Отпечаток профиля без запуска службы",
                    "java -jar dinamika_back.jar print-features --config=\"" + ceiling.ceilingFile() + "\"",
                    "отпечаток " + ceiling.fingerprint()));
        }
        rows.add(selfCheckRow("Сборка фронтенда не изменена",
                "node build/verify-dist.mjs dist; Get-FileHash по файлам dist против SHA256SUMS", "суммы совпадают"));
        return rows;
    }

    private static Map<String, Object> selfCheckRow(String title, String command, String expected) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", title);
        row.put("command", command);
        row.put("expected", expected);
        return row;
    }

    // ===================== 12. Роли =====================

    private Map<String, Object> roles(FeatureSnapshot ceiling, String roleSource) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("source", roleSource);
        section.put("sourceText", "db".equals(roleSource) ? "роль из БД на каждом запросе, ключ токенов установки"
                : "роль из токена, ключ токенов общий — роли функций не гарантируются (до пакета B10)");
        Map<String, Long> users = usersByRole();
        List<Map<String, Object>> matrix = new ArrayList<>();
        Set<String> listed = new LinkedHashSet<>();
        for (PublicEndpoints.RoleRights rights : PublicEndpoints.roleMatrix(ceiling)) {
            listed.add(rights.role());
            matrix.add(roleRow(rights.role(), rights.title(), users == null ? null : users.getOrDefault(rights.role(), 0L),
                    can(rights)));
        }
        // Роли из БД, которых код не знает: права вошедшего пользователя без особых ролей
        if (users != null) {
            for (Map.Entry<String, Long> e : users.entrySet()) {
                if (listed.contains(e.getKey())) continue;
                matrix.add(roleRow(e.getKey(), e.getKey(), e.getValue(),
                        List.of("права вошедшего пользователя без особых ролей", "функции с ролями в потолке — нет")));
            }
        }
        section.put("matrix", matrix);
        return section;
    }

    private static Map<String, Object> roleRow(String role, String title, Long users, List<String> can) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("role", role);
        row.put("title", title);
        row.put("users", users);
        row.put("can", can);
        return row;
    }

    private static List<String> can(PublicEndpoints.RoleRights rights) {
        List<String> can = new ArrayList<>();
        List<String> denied = rights.features().entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey).toList();
        can.add(denied.isEmpty() ? "все функции, разрешённые потолком" : "функции, разрешённые потолком, кроме: "
                + String.join(", ", denied) + " (роли функций в потолке)");
        if (rights.readOnly()) can.add("вне профиля безопасности и переключателей только читает");
        if (rights.manager()) can.add("права руководителя: видео станций, настройки камер, правка карты и ГПП");
        if (rights.securityProfile()) can.add("страница «Безопасность» и отчёт «Профиль безопасности»");
        if (rights.switchesDisable()) can.add("выключает функции переключателями (этап 2)");
        if (rights.switchesEnable()) can.add("включает функции обратно (этап 2)");
        return can;
    }

    private Map<String, Long> usersByRole() {
        List<Map<String, Object>> rows = query(t -> t.queryForList(
                "SELECT r.name AS role, count(u.id) AS users FROM roles r LEFT JOIN users u ON u.role_id = r.id "
                        + "GROUP BY r.name ORDER BY r.name"));
        if (rows == null) return null;
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object users = row.get("users");
            result.put(AppRoles.roleName(String.valueOf(row.get("role"))), users instanceof Number n ? n.longValue() : 0L);
        }
        return result;
    }

    // ===================== 13. Использование =====================

    private Map<String, Object> usage() {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("days", USAGE_DAYS);
        AccessLogRepository repository = accessLog.getIfAvailable();
        List<Map<String, Object>> rows = new ArrayList<>();
        if (repository != null) {
            List<Object[]> data;
            try {
                data = repository.usageSince(LocalDateTime.now(clock).minusDays(USAGE_DAYS));
            } catch (RuntimeException e) {
                data = List.of();
            }
            Map<String, Map<String, Object>> byActor = new TreeMap<>();
            for (Object[] r : data) {
                String actor = String.valueOf(r[0]);
                Map<String, Object> row = byActor.computeIfAbsent(actor, a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("actor", a);
                    m.put("role", r[1]);
                    m.put("counts", new TreeMap<String, Long>());
                    return m;
                });
                @SuppressWarnings("unchecked")
                Map<String, Long> counts = (Map<String, Long>) row.get("counts");
                counts.merge(String.valueOf(r[2]), ((Number) r[3]).longValue(), Long::sum);
            }
            rows.addAll(byActor.values());
        }
        section.put("rows", rows);
        return section;
    }

    // ===================== Оговорки и пробы =====================

    private List<String> caveats(FeatureSnapshot snapshot, FeatureSnapshot ceiling, String roleSource,
                                 Map<String, Object> dbUser) {
        List<String> caveats = new ArrayList<>();
        caveats.add("Переключатель администратора — это уровень L2 внутри потолка (режим " + ceiling.param(CeilingParam.ADMIN_MODE)
                + "). Если нужно «нельзя включить», функция выключается потолком. По умолчанию (disable-only) администратор "
                + "системы может только выключать, включает обратно только SECURITY.");
        caveats.add("Подфункции видео и интеграции закрыты маршрутом (L2): бины модуля есть, если модуль разрешён потолком.");
        caveats.add("Скачивание видео и выгрузка при разрешённом просмотре — L1: кто видит видео или таблицу, может их "
                + "сохранить. Для запрета выключается просмотр; каждая отдача видео и выгрузка есть в журнале доступа.");
        caveats.add("Закрытая функция отвечает 404 и анониму, и администратору. Полной неотличимости от несуществующего "
                + "пути нет: аноним на несуществующем пути получает 403.");
        caveats.add("Суперпользователь БД обходит триггеры журналов. Правку записей журнала профиля выявляет HMAC-цепочка "
                + "(ключ вне БД), удаление хвоста — сверка головы цепочки с бумагой и файловым логом. Приложение должно "
                + "работать под отдельной ролью БД без UPDATE/DELETE/TRUNCATE на журналы (deploy/db/roles.sql)"
                + (Boolean.TRUE.equals(dbUser.get("superuser")) ? " — сейчас это не так: приложение работает под "
                + "суперпользователем." : ".") + " Кто управляет и сервером приложения, и ключом HMAC, тот может всё.");
        if (!journal.keyed()) {
            caveats.add("Журнал профиля ведётся без ключа HMAC (ключа нет в файле секретов): цепочка SHA-256 выявляет "
                    + "правку записей, но не подделку администратором БД с пересчётом цепочки.");
        }
        caveats.add("db".equals(roleSource) ? "Роли: роль пользователя берётся из БД на каждом запросе, ключ токенов — свой у установки."
                : "Роли: до пакета B10 роль берётся из токена, а ключ токенов общий для всех установок — у функций с ролями "
                + "роль не гарантируется.");
        caveats.add("Конфигурации nginx в поставке нет: раздел «вне приложения» (порты 80/443, /sw.js) заполняется при "
                + "внедрении по шаблону deploy/nginx.");
        caveats.add("Секреты: отчёт показывает только их источник. Значения из репозитория — повод выполнить init-secrets "
                + "(пакет B10); после него со значениями из репозитория служба не запускается.");
        if (snapshot.isOn(Feature.VIDEO)) {
            caveats.add("Пароль камеры передаётся ffmpeg в адресе (аргументы процесса), пока пакет B11 не перенёс его "
                    + "в зашифрованное хранилище; check-surface аргументы процессов не выводит.");
        }
        if (ceiling.isOn(Feature.ANONYMOUS)) {
            caveats.add("Включён анонимный доступ (режим табло): часть API отвечает без входа — список в разделе «Входящие». "
                    + "Перед выключением проверить, не ходят ли станции и табло в API без входа (открытый вопрос 15.1).");
        }
        return caveats;
    }

    /** Пробы из браузера: адрес пробы каждой выключенной функции, у которой есть маршруты */
    private List<Map<String, Object>> probes(FeatureSnapshot snapshot) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Feature f : Feature.real()) {
            if (snapshot.isOn(f) || f.isUiOnly() || f.probe() == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("feature", f.key());
            row.put("url", f.probe());
            result.add(row);
        }
        return result;
    }

    // ===================== Служебное =====================

    private static Map<String, Object> check(String text, Boolean ok) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("text", text);
        row.put("ok", ok);
        return row;
    }

    private StartupProfileAuditor.Status auditorStatus() {
        StartupProfileAuditor a = auditor.getIfAvailable();
        return a == null ? null : a.status();
    }

    /** Запрос к БД: нет БД или ошибка — null (раздел отчёта показывает «нет данных», отчёт не падает) */
    private <T> T query(Function<JdbcTemplate, T> action) {
        JdbcTemplate template = jdbc.getIfAvailable();
        if (template == null) return null;
        try {
            return action.apply(template);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Значение для сравнения отпечатков в событиях отчёта */
    static boolean sameFingerprint(String reported, FeatureSnapshot ceiling) {
        return reported != null && Objects.equals(reported.toLowerCase(Locale.ROOT), ceiling.fingerprint());
    }
}
