package com.example.dinamika_back.features;

import com.example.dinamika_back.features.audit.AccessLogRepository;
import com.example.dinamika_back.features.journal.FeatureJournal;
import com.example.dinamika_back.features.journal.FeatureLogEntry;
import com.example.dinamika_back.features.journal.JournalHmac;
import com.example.dinamika_back.features.report.RuntimeInspector;
import com.example.dinamika_back.features.report.SecurityProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Отчёт «Профиль безопасности» (раздел 9.2 проекта): состав детерминирован (одни и те же разделы и поля в одном
 * порядке), шапка с отпечатком и головой журнала, функции с фактическим уровнем и самопроверками, честные пометки
 * (роли до B10 не гарантируются, секреты из репозитория), значений секретов в отчёте нет.
 */
class SecurityProfileServiceTest {

    private static final String JWT_KEY = "{\"kty\":\"oct\",\"k\":\"report-test-only-secret-key\"}";
    private static final String DB_PASSWORD = "report-test-only-db-password";
    private static final String API_KEY = "report-test-only-api-key";
    private static final ObjectMapper JSON = new ObjectMapper();

    private FeatureJournalTest.SecurityLogCapture securityLog;
    private FeatureSnapshot standard;
    private FeatureJournalTest.MemoryLog journalRows;
    private SecurityProfileService service;
    private StandardEnvironment environment;
    private RuntimeInspector runtime;

    @BeforeEach
    void setUp() {
        securityLog = new FeatureJournalTest.SecurityLogCapture();
        standard = FeatureResolver.fromProperties(Map.of("awms.features.preset", "standard",
                "awms.features.export.roles", "ADMIN,DIRECTOR"));
        FeatureService features = new FeatureService(standard);
        journalRows = new FeatureJournalTest.MemoryLog();
        FeatureJournal journal = FeatureJournalTest.journal(journalRows, null, standard);
        journal.append(FeatureJournal.Event.of(FeatureLogEntry.Type.STARTUP));
        journal.append(FeatureJournal.Event.of(FeatureLogEntry.Type.CEILING_CHANGED).reason("начальный профиль установки"));

        // Без системных свойств и окружения процесса: отчёт видит только то, что задаёт тест
        environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        Map<String, Object> jar = new LinkedHashMap<>();
        jar.put("jwt.cookie-token-key", JWT_KEY);
        jar.put("spring.datasource.password", DB_PASSWORD);
        jar.put("spring.datasource.url", "jdbc:postgresql://db-host:5432/dinamika?useUnicode=true");
        jar.put("api.key.awms", API_KEY);
        jar.put("video.storage-dir", "нет-такого-каталога/video-archive");
        environment.getPropertySources().addLast(new MapPropertySource(
                "Config resource 'class path resource [application.properties]' via location 'optional:classpath:/'", jar));

        GenericApplicationContext context = new GenericApplicationContext();
        context.refresh();
        runtime = new RuntimeInspector(context, environment);

        AccessLogRepository accessLog = mock(AccessLogRepository.class);
        when(accessLog.usageSince(any(LocalDateTime.class))).thenReturn(List.of(
                new Object[]{"director_test", "DIRECTOR", "VIDEO_VIEWED", 3L},
                new Object[]{"director_test", "DIRECTOR", "EXPORT", 12L},
                new Object[]{"admin_test", "ADMIN", "EXPORT", 1L}));

        Properties info = new Properties();
        info.setProperty("group", "com.example");
        info.setProperty("artifact", "dinamika_back");
        info.setProperty("version", "0.0.1-SNAPSHOT");
        info.setProperty("time", "2026-10-14T05:00:00Z");

        service = new SecurityProfileService(features, journal, provider(null), provider(accessLog), runtime,
                provider((JdbcTemplate) null), provider(new BuildProperties(info)), environment,
                Clock.fixed(Instant.parse("2026-10-14T05:00:00Z"), ZoneId.of("Asia/Yekaterinburg")), "test-host");
    }

    @AfterEach
    void tearDown() {
        securityLog.close();
    }

    @Test
    void compositionIsDeterministic() throws Exception {
        Map<String, Object> first = new LinkedHashMap<>(service.report(viewer("ib", "SECURITY")));
        Map<String, Object> second = new LinkedHashMap<>(service.report(viewer("ib", "SECURITY")));
        assertThat(first.keySet()).containsExactly("generatedAt", "viewer", "header", "features", "params", "absent",
                "runtime", "inbound", "outbound", "background", "storedData", "components", "journal", "selfCheck",
                "roles", "usage", "caveats", "probes");
        // Потоки JVM живут своей жизнью: их сводка — единственное, что меняется между вызовами
        for (Map<String, Object> r : List.of(first, second)) {
            r.remove("generatedAt");
            @SuppressWarnings("unchecked")
            Map<String, Object> background = new LinkedHashMap<>((Map<String, Object>) r.get("background"));
            background.remove("threads");
            r.put("background", background);
        }
        assertThat(JSON.writeValueAsString(first)).isEqualTo(JSON.writeValueAsString(second));
    }

    @Test
    @SuppressWarnings("unchecked")
    void headerCarriesFingerprintJournalHeadAndHonestSourceOfSecretsAndRoles() throws Exception {
        Map<String, Object> report = service.report(viewer("ib", "SECURITY"));
        Map<String, Object> header = (Map<String, Object>) report.get("header");
        assertThat(header.get("fingerprint")).isEqualTo(standard.fingerprint());
        assertThat(header.get("revision")).isEqualTo(standard.revision());
        assertThat(header.get("preset")).isEqualTo("standard");
        assertThat(header.get("presetSource")).isEqualTo("test");
        assertThat(header.get("adminMode")).isEqualTo("disable-only");
        assertThat(header.get("host")).isEqualTo("test-host");
        assertThat(((Map<String, Object>) header.get("backend")).get("version")).isEqualTo("0.0.1-SNAPSHOT");
        FeatureLogEntry last = journalRows.last();
        assertThat(header.get("journalHead")).isEqualTo(Map.of("id", last.getId(), "hash", last.getRowHash()));
        Map<String, Object> secrets = (Map<String, Object>) header.get("secrets");
        assertThat(secrets.get("state")).isEqualTo("repository");
        assertThat((String) secrets.get("text")).contains("ключа HMAC журнала нет");
        assertThat(header.get("roleSource")).isEqualTo("token");

        // Значений секретов в отчёте нет — только их источник
        String json = JSON.writeValueAsString(report);
        assertThat(json).doesNotContain(JWT_KEY).doesNotContain("report-test-only-secret-key")
                .doesNotContain(DB_PASSWORD).doesNotContain(API_KEY);

        Map<String, Object> viewer = (Map<String, Object>) report.get("viewer");
        assertThat(viewer.get("login")).isEqualTo("ib");
        assertThat(viewer.get("role")).isEqualTo("SECURITY");
        assertThat(viewer.get("canPurge")).as("команды удаления данных (B11) ещё нет").isEqualTo(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void featuresShowActualLevelSelfChecksAndRoleGuarantee() {
        Map<String, Object> report = service.report(viewer("director_test", "DIRECTOR"));
        List<Map<String, Object>> rows = (List<Map<String, Object>>) report.get("features");
        assertThat(rows).extracting(r -> r.get("key")).containsExactlyElementsOf(
                Feature.real().stream().map(Feature::key).toList());

        Map<String, Object> video = row(rows, "video");
        assertThat(video.get("enabled")).isEqualTo(false);
        assertThat(video.get("reason")).isEqualTo("PRESET");
        assertThat(video.get("level")).as("бина VideoController нет — модуль не загружен").isEqualTo("L3");
        assertThat(video.get("probe")).isEqualTo("/api/video/status");
        List<Map<String, Object>> checks = (List<Map<String, Object>>) video.get("checks");
        assertThat(checks).extracting(c -> c.get("text"), c -> c.get("ok")).contains(
                org.assertj.core.groups.Tuple.tuple("бин VideoController отсутствует", true),
                org.assertj.core.groups.Tuple.tuple("потоков live-*, clip-*, live-idle* нет", true),
                org.assertj.core.groups.Tuple.tuple("дочерних процессов ffmpeg и ffprobe нет", true));

        Map<String, Object> live = row(rows, "video.live");
        assertThat(live.get("reason")).as("в standard подфункции видео выключены самим пресетом").isEqualTo("PRESET");
        assertThat(live.get("level")).as("предок не загружен").isEqualTo("L3");

        Map<String, Object> export = row(rows, "export");
        assertThat(export.get("enabled")).isEqualTo(true);
        assertThat(export.get("level")).isEqualTo("L1");
        assertThat(export.get("roles")).isEqualTo(List.of("ADMIN", "DIRECTOR"));
        assertThat(export.get("rolesGuaranteed")).as("до B10 роль из токена").isEqualTo(false);
        assertThat((String) export.get("levelText")).contains("клиент сохранить может");
        assertThat(row(rows, "map").get("rolesGuaranteed")).isNull();
        assertThat((List<?>) row(rows, "map").get("checks")).isEmpty();

        List<Map<String, Object>> probes = (List<Map<String, Object>>) report.get("probes");
        assertThat(probes).extracting(p -> p.get("feature")).contains("video", "orders", "websocket")
                .doesNotContain("pwa", "map", "export");
        List<Map<String, Object>> absent = (List<Map<String, Object>>) report.get("absent");
        assertThat(absent).extracting(a -> a.get("key"))
                .containsExactly("ai", "telemetry", "remote", "autoupdate", "push", "cloud", "mail");
    }

    @Test
    @SuppressWarnings("unchecked")
    void storedDataRolesUsageAndJournalSections() {
        Map<String, Object> report = service.report(viewer("admin_test", "ADMIN"));

        List<Map<String, Object>> stored = (List<Map<String, Object>>) report.get("storedData");
        Map<String, Object> video = stored.stream().filter(s -> s.get("feature").equals("video")).findFirst().orElseThrow();
        assertThat(video.get("note")).isEqualTo("хранится, доступ закрыт");
        assertThat(video.get("retentionDays")).isEqualTo(30);
        assertThat(video.get("purgeAllowed")).isEqualTo(false);
        assertThat((List<Map<String, Object>>) video.get("tables")).extracting(t -> t.get("name"))
                .containsExactly("video_camera", "video_record", "video_record_file");
        assertThat(stored).extracting(s -> s.get("feature")).doesNotContain("map.plan", "model3d");

        Map<String, Object> outbound = (Map<String, Object>) report.get("outbound");
        assertThat(outbound.get("database")).isEqualTo("db-host:5432/dinamika");
        assertThat(outbound.get("syncService")).as("orders выключен в standard").isNull();
        assertThat(outbound.get("restTemplates")).isEqualTo(0);

        Map<String, Object> roles = (Map<String, Object>) report.get("roles");
        assertThat(roles.get("source")).isEqualTo("token");
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) roles.get("matrix");
        assertThat(matrix).extracting(r -> r.get("role")).startsWith("ADMIN", "DIRECTOR", "OPERATOR", "SECURITY");
        Map<String, Object> operator = matrix.stream().filter(r -> r.get("role").equals("OPERATOR")).findFirst().orElseThrow();
        assertThat((List<String>) operator.get("can")).first().asString().contains("кроме: export");
        Map<String, Object> security = matrix.stream().filter(r -> r.get("role").equals("SECURITY")).findFirst().orElseThrow();
        assertThat((List<String>) security.get("can")).contains("вне профиля безопасности и переключателей только читает",
                "включает функции обратно (этап 2)");

        Map<String, Object> usage = (Map<String, Object>) report.get("usage");
        assertThat(usage.get("days")).isEqualTo(30);
        List<Map<String, Object>> usageRows = (List<Map<String, Object>>) usage.get("rows");
        assertThat(usageRows).extracting(r -> r.get("actor")).containsExactly("admin_test", "director_test");
        assertThat(usageRows.get(1).get("counts")).isEqualTo(Map.of("EXPORT", 12L, "VIDEO_VIEWED", 3L));

        Map<String, Object> journal = (Map<String, Object>) report.get("journal");
        assertThat(journal.get("chainValid")).isEqualTo(true);
        assertThat(journal.get("keyed")).isEqualTo(false);
        assertThat(journal.get("keyId")).isEqualTo(JournalHmac.NO_KEY);
        assertThat((List<Map<String, Object>>) journal.get("entries")).extracting(e -> e.get("type"))
                .containsExactly("CEILING_CHANGED", "STARTUP");

        List<String> caveats = (List<String>) report.get("caveats");
        assertThat(caveats).anyMatch(c -> c.contains("роль не гарантируется"))
                .anyMatch(c -> c.contains("без ключа HMAC"));

        List<Map<String, Object>> selfCheck = (List<Map<String, Object>>) report.get("selfCheck");
        assertThat(selfCheck).extracting(c -> c.get("command")).anyMatch(c -> String.valueOf(c).startsWith("curl -I "));
    }

    @Test
    @SuppressWarnings("unchecked")
    void brokenChainIsReportedInHeaderAndJournal() {
        journalRows.rows.firstEntry().getValue().setReason("подменено");
        Map<String, Object> report = service.report(viewer("ib", "SECURITY"));
        Map<String, Object> journal = (Map<String, Object>) report.get("journal");
        assertThat(journal.get("chainValid")).isEqualTo(false);
        assertThat(journal.get("brokenAt")).isEqualTo(journalRows.rows.firstKey());
        assertThat((List<String>) ((Map<String, Object>) report.get("header")).get("warnings"))
                .anyMatch(w -> w.contains("цепочка нарушена на записи"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dependencyWarningsAreCollapsedIntoOneLine() {
        FeatureSnapshot noVideo = FeatureResolver.fromProperties(Map.of("awms.features.preset", "full",
                "awms.features.video.enabled", "false"));
        assertThat(noVideo.warnings()).hasSizeGreaterThan(3);
        SecurityProfileService s = new SecurityProfileService(new FeatureService(noVideo),
                FeatureJournalTest.journal(new FeatureJournalTest.MemoryLog(), null, noVideo), provider(null),
                provider(null), runtime, provider((JdbcTemplate) null), provider(null), environment,
                Clock.systemUTC(), "test-host");
        List<String> warnings = (List<String>) ((Map<String, Object>) s.report(viewer("ib", "SECURITY")).get("header"))
                .get("warnings");
        assertThat(warnings).noneMatch(w -> w.startsWith("Функция video."))
                .anyMatch(w -> w.startsWith("Выключены вслед за функцией, от которой зависят: video.live, video.clips"));
    }

    // ===================== Вспомогательное =====================

    private static Map<String, Object> row(List<Map<String, Object>> rows, String key) {
        return rows.stream().filter(r -> r.get("key").equals(key)).findFirst().orElseThrow();
    }

    private static Authentication viewer(String login, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(login, null, List.of(new SimpleGrantedAuthority(role)));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
