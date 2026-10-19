package com.example.dinamika_back.features;

import com.example.dinamika_back.features.audit.AccessAuditInterceptor;
import com.example.dinamika_back.features.audit.AccessLogEntry;
import com.example.dinamika_back.features.audit.AccessLogRepository;
import com.example.dinamika_back.features.audit.JpaAccessAudit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Журнал доступа (раздел 10.2 проекта): перехватчик MVC пишет события по флагу {@code .audited()} у правил реестра
 * только при ответе 2xx; отдача {@code /content} пишет {@code VIDEO_VIEWED} один раз на (пользователь, файл, 10 мин)
 * при запросах {@code Range}; 404 и HEAD не пишутся; у выгрузки — заголовок и число строк, без данных.
 */
class AccessAuditInterceptorTest {

    private MemoryAccessLog log;
    private MovableClock clock;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        log = new MemoryAccessLog();
        clock = new MovableClock(Instant.parse("2026-10-14T05:00:00Z"));
        JpaAccessAudit audit = audit(log, clock);
        @SuppressWarnings("unchecked")
        ObjectProvider<com.example.dinamika_back.features.AccessAudit> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(audit);
        mvc = MockMvcBuilders.standaloneSetup(new StubVideoController(), new StubExportController())
                .addInterceptors(new AccessAuditInterceptor(provider))
                .setControllerAdvice(new AccessAuditInterceptor.ExportBodyAdvice())
                .build();
        signIn("director_test", "DIRECTOR");
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void contentWritesVideoViewedOncePerUserFileAndTenMinutes() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/video/files/15/content").header("Range", "bytes=" + i * 100 + "-"))
                    .andExpect(status().isPartialContent());
        }
        assertThat(log.rows).hasSize(1);
        AccessLogEntry viewed = log.rows.get(0);
        assertThat(viewed.getEventType()).isEqualTo("VIDEO_VIEWED");
        assertThat(viewed.getFeatureKey()).isEqualTo("video.archive");
        assertThat(viewed.getObjectRef()).isEqualTo("15");
        assertThat(viewed.getActor()).isEqualTo("director_test");
        assertThat(viewed.getActorRole()).isEqualTo("DIRECTOR");
        assertThat(viewed.getClientIp()).isEqualTo("127.0.0.1");
        assertThat(viewed.getDetails()).contains("\"range\":true").contains("\"status\":206");

        // Другой файл и другой пользователь — отдельные записи
        mvc.perform(get("/api/video/files/16/content")).andExpect(status().isOk());
        signIn("admin_test", "ADMIN");
        mvc.perform(get("/api/video/files/15/content")).andExpect(status().isOk());
        assertThat(log.rows).hasSize(3);

        // Через 10 минут тот же просмотр пишется снова
        signIn("director_test", "DIRECTOR");
        clock.advanceMinutes(11);
        mvc.perform(get("/api/video/files/15/content")).andExpect(status().isOk());
        assertThat(log.rows).hasSize(4);
    }

    @Test
    void failedAndHeadResponsesAreNotWritten() throws Exception {
        mvc.perform(get("/api/video/files/404/content")).andExpect(status().isNotFound());
        mvc.perform(head("/api/video/files/15/download")).andExpect(status().isOk());
        mvc.perform(get("/api/video/records")).andExpect(status().isOk());
        assertThat(log.rows).isEmpty();
    }

    @Test
    void downloadAndModelSourceAreWritten() throws Exception {
        mvc.perform(get("/api/video/clips/c-77/download")).andExpect(status().isOk());
        mvc.perform(get("/api/nomenclature/models/3d/9/source")).andExpect(status().isOk());
        assertThat(log.rows).extracting(AccessLogEntry::getEventType, AccessLogEntry::getFeatureKey,
                AccessLogEntry::getObjectRef).containsExactly(
                org.assertj.core.groups.Tuple.tuple("VIDEO_DOWNLOADED", "video.download", "c-77"),
                org.assertj.core.groups.Tuple.tuple("MODEL3D_SOURCE", "model3d", "9"));
    }

    @Test
    void exportWritesTitleAndRowCountWithoutData() throws Exception {
        String body = "{\"title\":\"Номенклатура\",\"columns\":[\"name\"],\"data\":[{\"name\":\"Секретная деталь\"},"
                + "{\"name\":\"Вторая\"}]}";
        mvc.perform(post("/api/nomenclature/export-pdf").contentType("application/json").content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/nomenclature/export-pdf").contentType("application/json").content(body))
                .andExpect(status().isOk());
        assertThat(log.rows).hasSize(2);
        AccessLogEntry export = log.rows.get(0);
        assertThat(export.getEventType()).isEqualTo("EXPORT");
        assertThat(export.getFeatureKey()).isEqualTo("export");
        assertThat(export.getObjectRef()).isEqualTo("Номенклатура");
        assertThat(export.getDetails()).contains("\"rows\":2").contains("\"format\":\"pdf\"")
                .doesNotContain("Секретная деталь");
    }

    @Test
    void liveSnapshotsAreGluedButLiveSessionsAreNot() {
        JpaAccessAudit audit = audit(log, clock);
        for (int i = 0; i < 3; i++) {
            audit.record(AccessAudit.Event.VIDEO_LIVE_OPENED, Feature.VIDEO_LIVE, "1", Map.of("mode", "snapshot"));
            audit.record(AccessAudit.Event.VIDEO_LIVE_OPENED, Feature.VIDEO_LIVE, "1", Map.of("mode", "live"));
        }
        assertThat(log.rows).extracting(AccessLogEntry::getDetails)
                .containsExactly("{\"mode\":\"snapshot\"}", "{\"mode\":\"live\"}", "{\"mode\":\"live\"}", "{\"mode\":\"live\"}");
    }

    @Test
    void outsideRequestActorIsServiceAccount() {
        SecurityContextHolder.clearContext();
        audit(log, clock).record(AccessAudit.Event.OUTBOUND_SENT, Feature.ORDERS, "localhost:8085/api/x",
                Map.of("status", 200));
        AccessLogEntry sent = log.rows.get(0);
        assertThat(sent.getActor()).startsWith("os:");
        assertThat(sent.getClientIp()).isNull();
        assertThat(sent.getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 10, 14, 10, 0));
    }

    // ===================== Вспомогательное =====================

    private static void signIn(String login, String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(login, null,
                List.of(new SimpleGrantedAuthority(role))));
    }

    /** Журнал доступа над репозиторием в памяти и своими часами */
    private static JpaAccessAudit audit(AccessLogRepository repository, Clock clock) {
        return new JpaAccessAudit(repository, clock);
    }

    /**
     * Отдача видео: 206 на Range, 404 на несуществующий файл. Заглушки — внутренние (не static) классы: сканер
     * контроллеров (FeatureCatalogContractTest, полный контекст) берёт только самостоятельные классы и их не видит
     */
    @RestController
    class StubVideoController {
        @GetMapping("/api/video/files/{id}/content")
        ResponseEntity<String> content(@PathVariable long id,
                                       @org.springframework.web.bind.annotation.RequestHeader(value = "Range",
                                               required = false) String range) {
            if (id == 404) return ResponseEntity.notFound().build();
            return ResponseEntity.status(range == null ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT).body("mp4");
        }

        @GetMapping("/api/video/files/{id}/download")
        String download(@PathVariable long id) {
            return "mp4";
        }

        @GetMapping("/api/video/clips/{clipId}/download")
        String clip(@PathVariable String clipId) {
            return "mp4";
        }

        @GetMapping("/api/video/records")
        List<String> records() {
            return List.of();
        }

        @GetMapping("/api/nomenclature/models/3d/{id}/source")
        String source(@PathVariable long id) {
            return "step";
        }
    }

    /** Выгрузка: тело {title, columns, data} как у контроллеров справочников */
    @RestController
    class StubExportController {
        @PostMapping("/api/nomenclature/export-pdf")
        String export(@RequestBody Map<String, Object> request) {
            return "pdf";
        }
    }

    /** Журнал доступа в памяти */
    static final class MemoryAccessLog implements AccessLogRepository {
        final List<AccessLogEntry> rows = new ArrayList<>();

        @Override
        public AccessLogEntry save(AccessLogEntry entry) {
            entry.setId((long) rows.size() + 1);
            rows.add(entry);
            return entry;
        }

        @Override
        public long count() {
            return rows.size();
        }

        @Override
        public List<Object[]> usageSince(LocalDateTime since) {
            return List.of();
        }
    }

    /** Часы, которые тест переводит вперёд */
    static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        void advanceMinutes(long minutes) {
            now = now.plusSeconds(minutes * 60);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.ofHours(5);
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
