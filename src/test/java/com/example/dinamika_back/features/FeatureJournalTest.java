package com.example.dinamika_back.features;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.read.ListAppender;
import com.example.dinamika_back.features.journal.FeatureJournal;
import com.example.dinamika_back.features.journal.FeatureLogEntry;
import com.example.dinamika_back.features.journal.FeatureLogRepository;
import com.example.dinamika_back.features.journal.JournalHmac;
import com.example.dinamika_back.features.journal.StartupProfileAuditor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Журнал профиля (раздел 10.1 проекта): HMAC-цепочка выявляет правку любой записи, удаление и вставку строк,
 * пересчёт без ключа; после каждой записи в файловый лог AWMS.SECURITY уходит строка JOURNAL_HEAD; аудит старта
 * пишет STARTUP и CEILING_CHANGED с разницей профилей и сверяет голову цепочки с файловым логом.
 * База не нужна: репозиторий — в памяти, хэши считает настоящий {@link JournalHmac}.
 */
class FeatureJournalTest {

    private static final String KEY = "test-only-hmac-key-for-journal-tests-0001";
    private static final ObjectMapper JSON = new ObjectMapper();

    private SecurityLogCapture securityLog;

    @BeforeEach
    void captureSecurityLog() {
        securityLog = new SecurityLogCapture();
    }

    @AfterEach
    void releaseSecurityLog() {
        securityLog.close();
    }

    // ===================== Цепочка =====================

    @Test
    void appendBuildsKeyedChainAndVerifies() {
        MemoryLog log = new MemoryLog();
        FeatureJournal journal = journal(log, KEY, full());
        FeatureLogEntry first = journal.append(event(FeatureLogEntry.Type.STARTUP));
        FeatureLogEntry second = journal.append(event(FeatureLogEntry.Type.REPORT_VIEWED));

        assertThat(first.getPrevHash()).isNull();
        assertThat(second.getPrevHash()).isEqualTo(first.getRowHash());
        assertThat(second.getKeyId()).startsWith("k").hasSize(13).isEqualTo(new JournalHmac(KEY).keyId());
        assertThat(second.getFingerprint()).isEqualTo(full().fingerprint());
        assertThat(second.getPreset()).isEqualTo("full");
        assertThat(second.getActor()).isEqualTo(FeatureJournal.serviceAccount());
        // HMAC, а не SHA-256 без ключа
        assertThat(second.getRowHash()).isNotEqualTo(new JournalHmac(null).hash(JournalHmac.NO_KEY, second));

        FeatureJournal.Verification v = journal.verify();
        assertThat(v.valid()).isTrue();
        assertThat(v.checked()).isEqualTo(2);
        assertThat(v.head().id()).isEqualTo(second.getId());
        assertThat(v.keyedSeen()).isTrue();
        assertThat(journal.head()).contains(new FeatureJournal.Head(second.getId(), second.getRowHash()));
    }

    @Test
    void editOfAnyRowIsDetected() {
        for (int target = 1; target <= 3; target++) {
            MemoryLog log = new MemoryLog();
            FeatureJournal journal = journal(log, KEY, full());
            for (int i = 0; i < 3; i++) journal.append(event(FeatureLogEntry.Type.STARTUP).reason("старт " + i));
            log.rows.get((long) target).setReason("подменено");

            FeatureJournal.Verification v = journal.verify();
            assertThat(v.valid()).as("правка записи %d", target).isFalse();
            assertThat(v.brokenAt()).isEqualTo(target);
            assertThat(v.problem()).contains("изменена");
        }
    }

    @Test
    void recalculationWithoutKeyIsDetected() {
        MemoryLog log = new MemoryLog();
        FeatureJournal journal = journal(log, KEY, full());
        for (int i = 0; i < 3; i++) journal.append(event(FeatureLogEntry.Type.STARTUP));

        // Администратор БД правит запись 2 и пересчитывает хвост цепочки без ключа (SHA-256), как умеет
        JournalHmac noKey = new JournalHmac(null);
        String prev = log.rows.get(1L).getRowHash();
        for (long id = 2; id <= 3; id++) {
            FeatureLogEntry row = log.rows.get(id);
            if (id == 2) row.setNewValue("подменено");
            row.setPrevHash(prev);
            row.setKeyId(JournalHmac.NO_KEY);
            row.setRowHash(noKey.rowHash(row));
            prev = row.getRowHash();
        }
        FeatureJournal.Verification v = journal.verify();
        assertThat(v.valid()).isFalse();
        assertThat(v.brokenAt()).isEqualTo(2);
        assertThat(v.problem()).contains("без ключа HMAC после записей с ключом");

        // Тот же пересчёт с поддельным идентификатором ключа: ключа на установке нет — запись не проверить
        FeatureLogEntry row2 = log.rows.get(2L);
        row2.setKeyId("k000000000000");
        FeatureJournal.Verification forged = journal.verify();
        assertThat(forged.valid()).isFalse();
        assertThat(forged.brokenAt()).isEqualTo(2);
        assertThat(forged.problem()).contains("ключом k000000000000");
    }

    @Test
    void deletedOrInsertedRowsAreDetected() {
        MemoryLog log = new MemoryLog();
        FeatureJournal journal = journal(log, KEY, full());
        for (int i = 0; i < 4; i++) journal.append(event(FeatureLogEntry.Type.STARTUP));

        log.rows.remove(2L);
        FeatureJournal.Verification middle = journal.verify();
        assertThat(middle.valid()).isFalse();
        assertThat(middle.brokenAt()).isEqualTo(3);
        assertThat(middle.problem()).contains("удалена");

        log.rows.remove(1L);
        FeatureJournal.Verification start = journal.verify();
        assertThat(start.brokenAt()).isEqualTo(3);
        assertThat(start.problem()).contains("начало журнала удалено");
    }

    @Test
    void chainWithoutKeyStillDetectsEdits() {
        MemoryLog log = new MemoryLog();
        FeatureJournal journal = journal(log, null, full());
        journal.append(event(FeatureLogEntry.Type.STARTUP));
        journal.append(event(FeatureLogEntry.Type.CEILING_CHANGED));
        assertThat(journal.keyed()).isFalse();
        assertThat(log.rows.get(1L).getKeyId()).isEqualTo(JournalHmac.NO_KEY);
        assertThat(journal.verify().valid()).isTrue();
        assertThat(journal.verify().keyedSeen()).isFalse();

        log.rows.get(1L).setActor("кто-то другой");
        assertThat(journal.verify().valid()).isFalse();
    }

    @Test
    void headLineIsWrittenAfterEveryEntry() {
        MemoryLog log = new MemoryLog();
        FeatureJournal journal = journal(log, KEY, full());
        FeatureLogEntry a = journal.append(event(FeatureLogEntry.Type.STARTUP));
        FeatureLogEntry b = journal.append(event(FeatureLogEntry.Type.PROBE_RUN));

        List<ILoggingEvent> heads = securityLog.events().stream()
                .filter(e -> e.getFormattedMessage().startsWith(FeatureJournal.HEAD_MARKER)).toList();
        assertThat(heads).extracting(ILoggingEvent::getFormattedMessage).containsExactly(
                "JOURNAL_HEAD id=" + a.getId() + " hash=" + a.getRowHash() + " type=STARTUP",
                "JOURNAL_HEAD id=" + b.getId() + " hash=" + b.getRowHash() + " type=PROBE_RUN");
        // Свой логгер (дочерний к AWMS.SECURITY, тот же файл): сверка при старте принимает только его строки
        assertThat(heads).extracting(ILoggingEvent::getLoggerName).containsOnly(FeatureJournal.HEAD_LOGGER);
    }

    @Test
    void valuesAreClippedAndCleanedBeforeHashing() {
        MemoryLog log = new MemoryLog();
        FeatureJournal journal = journal(log, KEY, full());
        FeatureLogEntry e = journal.append(event(FeatureLogEntry.Type.REPORT_EXPORTED)
                .reason("ж".repeat(1500) + "\0")
                .newValue("x\0y")
                .details(Map.of("big", "z".repeat(30_000))));
        assertThat(e.getReason()).hasSize(1000).endsWith("…");
        assertThat(e.getNewValue()).isEqualTo("xy");
        assertThat(e.getDetails()).contains("\"truncated\":true");
        assertThat(journal.verify().valid()).isTrue();
    }

    // ===================== Аудит старта =====================

    @Test
    void startupWritesInitialProfileThenOnlyStartupThenDifference(@TempDir Path logs) throws Exception {
        MemoryLog log = new MemoryLog();
        FeatureSnapshot full = full();

        auditor(journal(log, KEY, full), full, logs).audit();
        assertThat(log.types()).containsExactly("STARTUP", "CEILING_CHANGED");
        FeatureLogEntry initial = log.last();
        assertThat(initial.getReason()).isEqualTo("начальный профиль установки");
        assertThat(initial.getOldValue()).isNull();
        assertThat(initial.getNewValue()).isEqualTo(full.fingerprint());

        // Повторный старт без изменений — только STARTUP
        auditor(journal(log, KEY, full), full, logs).audit();
        assertThat(log.types()).containsExactly("STARTUP", "CEILING_CHANGED", "STARTUP");

        // Потолок изменён: video выключено — CEILING_CHANGED с разницей «было → стало»
        FeatureSnapshot noVideo = FeatureResolver.fromProperties(Map.of("awms.features.preset", "full",
                "awms.features.video.enabled", "false"));
        auditor(journal(log, KEY, noVideo), noVideo, logs).audit();
        assertThat(log.types()).endsWith("STARTUP", "CEILING_CHANGED");
        FeatureLogEntry changed = log.last();
        assertThat(changed.getOldValue()).isEqualTo(full.fingerprint());
        assertThat(changed.getNewValue()).isEqualTo(noVideo.fingerprint());
        JsonNode changes = JSON.readTree(changed.getDetails()).get("changes");
        List<String> keys = new ArrayList<>();
        changes.forEach(c -> keys.add(c.get("key").asText()));
        assertThat(keys).contains("awms.features.video", "awms.features.video.live", "awms.features.video.cameras")
                .doesNotContain("awms.features.map", "awms.video.retention-days");
        JsonNode video = null;
        for (JsonNode c : changes) if (c.get("key").asText().equals("awms.features.video")) video = c;
        assertThat(video.get("old").asText()).isEqualTo("on;roles=");
        assertThat(video.get("new").asText()).isEqualTo("off;roles=");
        assertThat(journal(log, KEY, noVideo).verify().valid()).isTrue();
    }

    @Test
    void fileChangeWithSameValuesIsRecordedToo(@TempDir Path dir) throws Exception {
        Path ceiling = dir.resolve("awms-features.properties");
        Files.writeString(ceiling, "awms.features.preset=standard\nawms.features.ticket=SZ-1\n", StandardCharsets.ISO_8859_1);
        FeatureSnapshot first = FeatureResolver.resolveFile(ceiling);
        Files.writeString(ceiling, "awms.features.preset=standard\nawms.features.ticket=SZ-2\n", StandardCharsets.ISO_8859_1);
        FeatureSnapshot second = FeatureResolver.resolveFile(ceiling);
        assertThat(second.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(second.ceilingFileSha256()).isNotEqualTo(first.ceilingFileSha256());

        MemoryLog log = new MemoryLog();
        auditor(journal(log, KEY, first), first, dir).audit();
        auditor(journal(log, KEY, second), second, dir).audit();
        FeatureLogEntry changed = log.last();
        assertThat(changed.getEventType()).isEqualTo("CEILING_CHANGED");
        assertThat(changed.getReason()).contains("итоговые значения те же");
        assertThat(changed.getTicket()).isEqualTo("SZ-2");
        JsonNode details = JSON.readTree(changed.getDetails());
        assertThat(details.get("changes")).isEmpty();
        assertThat(details.get("ticketOld").asText()).isEqualTo("SZ-1");
        assertThat(details.get("configSha256Old").asText()).isEqualTo(first.ceilingFileSha256());
    }

    @Test
    void profileReproducesFingerprintOfResolver() {
        for (String preset : List.of("isolated", "standard", "full")) {
            FeatureSnapshot s = FeatureResolver.fromProperties(Map.of("awms.features.preset", preset,
                    "awms.features.export.roles", "ADMIN,DIRECTOR", "awms.video.retention-days", "45"));
            assertThat(StartupProfileAuditor.fingerprintOf(StartupProfileAuditor.profile(s))).as(preset)
                    .isEqualTo(s.fingerprint());
        }
    }

    @Test
    void headInLogIsComparedWithDatabase(@TempDir Path logs) throws IOException {
        MemoryLog log = new MemoryLog();
        FeatureSnapshot full = full();
        FeatureJournal journal = journal(log, KEY, full);
        journal.append(event(FeatureLogEntry.Type.STARTUP));
        FeatureLogEntry second = journal.append(event(FeatureLogEntry.Type.STARTUP));
        journal.append(event(FeatureLogEntry.Type.STARTUP));

        // Нет строки головы в логе — не нарушение, но голова не сверена
        StartupProfileAuditor.HeadCheck none = auditor(journal, full, logs).checkHead();
        assertThat(none.mismatch()).isFalse();
        assertThat(none.unverified()).isTrue();
        assertThat(none.text()).contains("нет строки JOURNAL_HEAD").contains("записей в журнале: 3");

        // Голова в логе — запись 2: она в БД с тем же хэшем; записи после неё в БД — не нарушение
        Files.writeString(logs.resolve("awms-security.log"), headLine(second) + "\n"
                + "2026-10-14T10:00:01.000+05:00  INFO [main] AWMS.SECURITY : Профиль функций AWMS\n");
        StartupProfileAuditor.HeadCheck ok = auditor(journal, full, logs).checkHead();
        assertThat(ok.mismatch()).isFalse();
        assertThat(ok.unverified()).isFalse();
        assertThat(ok.logId()).isEqualTo(second.getId());

        // Хвост удалён: записи 2 и 3 в БД больше нет — нарушение, TAMPER пишется один раз
        log.rows.remove(3L);
        log.rows.remove(2L);
        StartupProfileAuditor auditor = auditor(journal, full, logs);
        StartupProfileAuditor.HeadCheck missing = auditor.checkHead();
        assertThat(missing.mismatch()).isTrue();
        assertThat(missing.text()).contains("хвост журнала удалён");
        auditor.audit();
        auditor(journal, full, logs).audit();
        assertThat(log.types().stream().filter("TAMPER"::equals)).hasSize(1);
        assertThat(auditor.status().problems()).anyMatch(p -> p.contains("хвост журнала удалён"));

        // Голова в прежнем файле ротации тоже находится
        Files.delete(logs.resolve("awms-security.log"));
        Files.writeString(logs.resolve("awms-security.1.log"), headLine(second) + "\n");
        assertThat(auditor(journal, full, logs).checkHead().logId()).isEqualTo(second.getId());
    }

    @Test
    void changedHeadRowIsTamper(@TempDir Path logs) throws IOException {
        MemoryLog log = new MemoryLog();
        FeatureSnapshot full = full();
        FeatureJournal journal = journal(log, KEY, full);
        FeatureLogEntry only = journal.append(event(FeatureLogEntry.Type.STARTUP));
        Files.writeString(logs.resolve("awms-security.log"), headLine(FeatureJournal.HEAD_LOGGER, only.getId(),
                "0".repeat(64)) + "\n");
        StartupProfileAuditor.HeadCheck check = auditor(journal, full, logs).checkHead();
        assertThat(check.mismatch()).isTrue();
        assertThat(check.text()).contains("отличается от головы цепочки");
    }

    @Test
    void markerInsideAnotherMessageIsNotHead(@TempDir Path logs) throws IOException {
        MemoryLog log = new MemoryLog();
        FeatureSnapshot full = full();
        FeatureJournal journal = journal(log, KEY, full);
        journal.append(event(FeatureLogEntry.Type.STARTUP));
        FeatureLogEntry second = journal.append(event(FeatureLogEntry.Type.STARTUP));
        FeatureLogEntry third = journal.append(event(FeatureLogEntry.Type.STARTUP));
        FeatureLogEntry fourth = journal.append(event(FeatureLogEntry.Type.STARTUP));
        String forged = "JOURNAL_HEAD id=999999 hash=" + "f".repeat(64) + " type=STARTUP";
        String older = "JOURNAL_HEAD id=" + second.getId() + " hash=" + second.getRowHash() + " type=STARTUP";

        // Настоящая голова — запись 4; после неё — строки, где маркер стоит внутри других сообщений: путь колбэка
        // (до исправления фильтр писал его раскодированным), INFO общего логгера, хвост после типа, чужой логгер
        Files.writeString(logs.resolve("awms-security.log"), String.join("\n",
                headLine(fourth),
                "2026-10-14T10:00:01.000+05:00  WARN [http-nio-8084-exec-1] AWMS.SECURITY : Колбэк заказов отклонён "
                        + "(401): POST /api/orders/" + forged + "/status с адреса 10.0.0.9 — нет заголовка X-API-Key",
                "2026-10-14T10:00:02.000+05:00  INFO [main] AWMS.SECURITY : Профиль: " + forged,
                "2026-10-14T10:00:03.000+05:00  INFO [main] AWMS.SECURITY.HEAD : " + forged + "/status",
                "2026-10-14T10:00:04.000+05:00  INFO [main] AWMS.SECURITY.HEADX : " + forged,
                "2026-10-14T10:00:05.000+05:00  INFO [main] com.example.Other : " + forged,
                "2026-10-14T10:00:06.000+05:00  WARN [main] AWMS.SECURITY.HEAD : " + forged,
                "2026-10-14T10:00:07.000+05:00  INFO [a] b] AWMS.SECURITY.HEAD : " + forged,
                forged,
                "Колбэк: " + older) + "\n");
        StartupProfileAuditor.HeadCheck check = auditor(journal, full, logs).checkHead();
        assertThat(check.logId()).isEqualTo(fourth.getId());
        assertThat(check.mismatch()).isFalse();

        // Администратор БД удалил хвост (записи 3 и 4) и подложил «голову» записи 2 HTTP-запросом — удаление видно
        log.rows.remove(fourth.getId());
        log.rows.remove(third.getId());
        Files.writeString(logs.resolve("awms-security.log"), "2026-10-14T10:00:08.000+05:00  WARN [x] AWMS.SECURITY : "
                + "Колбэк заказов отклонён (401): POST /api/orders/" + older + "/status\n", StandardOpenOption.APPEND);
        StartupProfileAuditor.HeadCheck missing = auditor(journal, full, logs).checkHead();
        assertThat(missing.logId()).isEqualTo(fourth.getId());
        assertThat(missing.mismatch()).isTrue();
        assertThat(missing.text()).contains("хвост журнала удалён");
    }

    @Test
    void headWrittenByRealLogPatternIsFound(@TempDir Path logs) throws IOException {
        // Шаблон аппендера из logback-spring.xml: сверка находит строки, которые пишет сам журнал, и не принимает
        // маркер из чужого сообщения того же файла
        MemoryLog log = new MemoryLog();
        FeatureSnapshot full = full();
        FeatureJournal journal = journal(log, KEY, full);
        Appender<ILoggingEvent> file = securityLog.toFile(logs.resolve("awms-security.log"));
        FeatureLogEntry last;
        try {
            journal.append(event(FeatureLogEntry.Type.STARTUP));
            last = journal.append(event(FeatureLogEntry.Type.CEILING_CHANGED));
            FeatureJournal.SECURITY.warn("Колбэк заказов отклонён (401): POST {} — нет заголовка",
                    "/api/orders/JOURNAL_HEAD id=999999 hash=" + "e".repeat(64) + " type=STARTUP/status");
            FeatureJournal.SECURITY.info("JOURNAL_HEAD id=999998 hash={} type=STARTUP", "d".repeat(64));
        } finally {
            securityLog.stopFile(file);
        }
        StartupProfileAuditor.HeadCheck check = auditor(journal, full, logs).checkHead();
        assertThat(check.logId()).isEqualTo(last.getId());
        assertThat(check.mismatch()).isFalse();
        assertThat(check.text()).contains("совпадает");
    }

    @Test
    void legacyHeadLinesAreUsedOnlyWithoutNewOnes(@TempDir Path logs) throws IOException {
        MemoryLog log = new MemoryLog();
        FeatureSnapshot full = full();
        FeatureJournal journal = journal(log, KEY, full);
        FeatureLogEntry first = journal.append(event(FeatureLogEntry.Type.STARTUP));
        FeatureLogEntry second = journal.append(event(FeatureLogEntry.Type.STARTUP));

        // Первый запуск после обновления: в логе только строки прежнего вида (общий логгер AWMS.SECURITY)
        Files.writeString(logs.resolve("awms-security.1.log"), headLine("AWMS.SECURITY", first.getId(),
                first.getRowHash()) + "\n");
        Files.writeString(logs.resolve("awms-security.log"), headLine("AWMS.SECURITY", second.getId(),
                second.getRowHash()) + "\n");
        assertThat(auditor(journal, full, logs).checkHead().logId()).isEqualTo(second.getId());

        // Есть строка нового вида (даже в прежнем файле ротации) — строки прежнего вида больше не учитываются
        Files.writeString(logs.resolve("awms-security.1.log"), headLine(first) + "\n");
        Files.writeString(logs.resolve("awms-security.log"), headLine("AWMS.SECURITY", 999_999L, "c".repeat(64))
                + "\n");
        StartupProfileAuditor.HeadCheck check = auditor(journal, full, logs).checkHead();
        assertThat(check.logId()).isEqualTo(first.getId());
        assertThat(check.mismatch()).isFalse();
    }

    @Test
    void unverifiedHeadIsWarnedButIsNotTamper(@TempDir Path logs) {
        MemoryLog log = new MemoryLog();
        FeatureSnapshot full = full();
        // Пустой журнал: первый запуск — сверять нечего, пометки нет
        StartupProfileAuditor firstStart = auditor(journal(log, KEY, full), full, logs);
        firstStart.audit();
        assertThat(firstStart.status().head().unverified()).isFalse();
        assertThat(firstStart.status().problems()).isEmpty();

        // Журнал не пуст, а файлового лога нет (удалён или не пишется): не TAMPER, но пометка в статусе и WARN
        StartupProfileAuditor auditor = auditor(journal(log, KEY, full), full, logs);
        auditor.audit();
        assertThat(auditor.status().head().unverified()).isTrue();
        assertThat(auditor.status().problems()).singleElement().satisfies(p ->
                assertThat(p).startsWith(StartupProfileAuditor.HEAD_UNVERIFIED).contains("нет строки JOURNAL_HEAD"));
        assertThat(log.types()).doesNotContain("TAMPER");
        assertThat(securityLog.events()).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getFormattedMessage()).startsWith(StartupProfileAuditor.HEAD_UNVERIFIED);
        });
    }

    @Test
    void deniedSummaryGoesToJournalHourly() throws Exception {
        MemoryLog log = new MemoryLog();
        FeatureSnapshot isolated = FeatureResolver.fromProperties(Map.of("awms.features.preset", "isolated"));
        FeatureService service = new FeatureService(isolated);
        FeatureGateFilter gate = new FeatureGateFilter(service);
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/video/status");
            request.setRemoteAddr("10.0.0.7");
            gate.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }
        @SuppressWarnings("unchecked")
        ObjectProvider<FilterRegistrationBean<FeatureGateFilter>> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(new FilterRegistrationBean<>(gate));
        StartupProfileAuditor auditor = newAuditor(journal(log, KEY, isolated), service, Path.of("нет-логов"), provider);

        auditor.writeDeniedSummary();
        FeatureLogEntry summary = log.last();
        assertThat(summary.getEventType()).isEqualTo("DENIED_SUMMARY");
        assertThat(summary.getFeatureKey()).isEqualTo("video");
        assertThat(summary.getNewValue()).isEqualTo("3");
        assertThat(JSON.readTree(summary.getDetails()).get("top").get(0).get("ip").asText()).isEqualTo("10.0.0.7");

        // Отказов с прошлой сводки нет — записи нет
        auditor.writeDeniedSummary();
        assertThat(log.rows).hasSize(1);
    }

    // ===================== Вспомогательное =====================

    private static FeatureSnapshot full() {
        return FeatureResolver.fromProperties(Map.of("awms.features.preset", "full"));
    }

    /** Строка головы, как её пишет журнал в awms-security.log (шаблон logback-spring.xml) */
    static String headLine(FeatureLogEntry entry) {
        return headLine(FeatureJournal.HEAD_LOGGER, entry.getId(), entry.getRowHash());
    }

    static String headLine(String logger, long id, String hash) {
        return "2026-10-14T10:00:00.000+05:00  INFO [main] " + logger + " : JOURNAL_HEAD id=" + id + " hash=" + hash
                + " type=STARTUP";
    }

    private static FeatureJournal.Event event(FeatureLogEntry.Type type) {
        return FeatureJournal.Event.of(type).actor(new FeatureJournal.Actor(FeatureJournal.serviceAccount(), null, null));
    }

    private static final AtomicLong CLOCK = new AtomicLong(Instant.parse("2026-10-14T05:00:00Z").toEpochMilli());

    /** Журнал над репозиторием в памяти; часы идут вперёд с каждым новым журналом */
    static FeatureJournal journal(MemoryLog log, String key, FeatureSnapshot ceiling) {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(CLOCK.addAndGet(1234)), ZoneId.of("Asia/Yekaterinburg"));
        return new FeatureJournal(log, new FeatureService(ceiling), new JournalHmac(key), "test-host", "test", clock);
    }

    private static StartupProfileAuditor auditor(FeatureJournal journal, FeatureSnapshot ceiling, Path logs) {
        @SuppressWarnings("unchecked")
        ObjectProvider<FilterRegistrationBean<FeatureGateFilter>> none = mock(ObjectProvider.class);
        return newAuditor(journal, new FeatureService(ceiling), logs, none);
    }

    private static StartupProfileAuditor newAuditor(FeatureJournal journal, FeatureService service, Path logs,
                                                    ObjectProvider<FilterRegistrationBean<FeatureGateFilter>> gate) {
        return new StartupProfileAuditor(journal, service, logs, gate, new MockEnvironment());
    }

    /**
     * Логгер AWMS.SECURITY на время теста пишет только в память: строки JOURNAL_HEAD тестового журнала не должны
     * попасть в logs/awms-security.log, если тестовый контекст Spring уже подключил к логгеру файл (иначе сверка
     * головы при старте полного контекста нашла бы чужую голову)
     */
    static final class SecurityLogCapture implements AutoCloseable {
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        private final Logger logger = (Logger) LoggerFactory.getLogger("AWMS.SECURITY");
        private final List<Appender<ILoggingEvent>> detached = new ArrayList<>();
        private final boolean additive;

        SecurityLogCapture() {
            additive = logger.isAdditive();
            logger.iteratorForAppenders().forEachRemaining(detached::add);
            detached.forEach(logger::detachAppender);
            logger.setAdditive(false);
            appender.start();
            logger.addAppender(appender);
        }

        List<String> messages() {
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        }

        List<ILoggingEvent> events() {
            return List.copyOf(appender.list);
        }

        /** На время теста — ещё и в файл, как аппендер AWMS_SECURITY_FILE (тот же шаблон из logback-spring.xml) */
        Appender<ILoggingEvent> toFile(Path file) throws IOException {
            String xml;
            try (var in = FeatureJournalTest.class.getClassLoader().getResourceAsStream("logback-spring.xml")) {
                assertThat(in).as("logback-spring.xml в classpath").isNotNull();
                xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            Matcher m = Pattern.compile("<appender name=\"AWMS_SECURITY_FILE\"[\\s\\S]*?<pattern>([^<]+)</pattern>")
                    .matcher(xml);
            assertThat(m.find()).as("шаблон аппендера AWMS_SECURITY_FILE").isTrue();
            LoggerContext context = logger.getLoggerContext();
            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setPattern(m.group(1));
            encoder.setCharset(StandardCharsets.UTF_8);
            encoder.start();
            FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
            fileAppender.setContext(context);
            fileAppender.setFile(file.toString());
            fileAppender.setEncoder(encoder);
            fileAppender.start();
            logger.addAppender(fileAppender);
            return fileAppender;
        }

        void stopFile(Appender<ILoggingEvent> fileAppender) {
            logger.detachAppender(fileAppender);
            fileAppender.stop();
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            detached.forEach(logger::addAppender);
            logger.setAdditive(additive);
        }
    }

    /** Журнал профиля в памяти: то же, что делает Spring Data над sys_feature_log */
    static final class MemoryLog implements FeatureLogRepository {
        final TreeMap<Long, FeatureLogEntry> rows = new TreeMap<>();
        private long sequence;

        @Override
        public FeatureLogEntry save(FeatureLogEntry entry) {
            rows.put(entry.getId(), entry);
            return entry;
        }

        @Override
        public Optional<FeatureLogEntry> findById(Long id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public long count() {
            return rows.size();
        }

        @Override
        public Integer lockChain(long key) {
            return 1;
        }

        @Override
        public Long nextId() {
            return ++sequence;
        }

        @Override
        public Optional<FeatureLogEntry> findTopByOrderByIdDesc() {
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.lastEntry().getValue());
        }

        @Override
        public Optional<FeatureLogEntry> findTopByEventTypeOrderByIdDesc(String eventType) {
            return rows.descendingMap().values().stream().filter(e -> e.getEventType().equals(eventType)).findFirst();
        }

        @Override
        public List<FeatureLogEntry> findByIdGreaterThanOrderByIdAsc(Long afterId, Pageable page) {
            return rows.tailMap(afterId, false).values().stream().limit(page.getPageSize()).toList();
        }

        @Override
        public List<FeatureLogEntry> findByOrderByIdDesc(Pageable page) {
            return rows.descendingMap().values().stream().limit(page.getPageSize()).toList();
        }

        @Override
        public List<FeatureLogEntry> findByEventTypeInOrderByIdDesc(Collection<String> eventTypes, Pageable page) {
            return rows.descendingMap().values().stream().filter(e -> eventTypes.contains(e.getEventType()))
                    .sorted(Comparator.comparing(FeatureLogEntry::getId).reversed()).limit(page.getPageSize()).toList();
        }

        List<String> types() {
            return rows.values().stream().map(FeatureLogEntry::getEventType).toList();
        }

        FeatureLogEntry last() {
            return rows.lastEntry().getValue();
        }
    }
}
