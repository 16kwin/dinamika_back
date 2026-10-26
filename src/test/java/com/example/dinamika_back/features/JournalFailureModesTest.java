package com.example.dinamika_back.features;

import com.example.dinamika_back.features.audit.AccessLogEntry;
import com.example.dinamika_back.features.audit.JpaAccessAudit;
import com.example.dinamika_back.features.journal.FeatureJournal;
import com.example.dinamika_back.features.journal.FeatureLogRepository;
import com.example.dinamika_back.features.journal.JournalHmac;
import com.example.dinamika_back.features.journal.StartupProfileAuditor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Сбой журналов на сервере команды не должен менять поведение службы (правило «как до выключателей»), а на заводе
 * строгий режим включается ключом потолка {@code awms.journal.required=true}:
 * <ul>
 *   <li>аудит профиля при старте: по умолчанию служба запускается, нарушение — в отчёте и плашке; строго — запуск
 *       отменяется;</li>
 *   <li>журнал доступа: по умолчанию действие выполняется, строго — сторож отказывает; повторы склеиваются без
 *       обращения к БД, запись идёт своим EntityManager и не держит соединение потока запроса;</li>
 *   <li>{@code OUTBOUND_BLOCKED} — в журнал профиля один раз на 10 минут на адрес и причину.</li>
 * </ul>
 */
class JournalFailureModesTest {

    private FeatureJournalTest.SecurityLogCapture securityLog;

    @BeforeEach
    void captureSecurityLog() {
        securityLog = new FeatureJournalTest.SecurityLogCapture();
    }

    @AfterEach
    void releaseSecurityLog() {
        securityLog.close();
        SecurityContextHolder.clearContext();
    }

    // ===================== Аудит профиля при старте =====================

    @Test
    void startupAuditFailureKeepsServiceRunningByDefault(@TempDir Path logs) {
        FeatureSnapshot full = FeatureResolver.fromProperties(Map.of("awms.features.preset", "full"));
        StartupProfileAuditor auditor = auditor(brokenJournal(full), full, logs);

        assertThatCode(() -> auditor.run(null)).doesNotThrowAnyException();
        assertThat(auditor.status().problems()).singleElement().satisfies(p ->
                assertThat(p).contains("аудит запуска не выполнен").contains("нет прав на sys_feature_log"));
        assertThat(securityLog.messages()).anySatisfy(m -> assertThat(m).contains("аудит запуска не выполнен")
                .contains("awms.journal.required"));
        auditor.destroy();
    }

    @Test
    void startupAuditFailureCancelsStartWhenJournalRequired(@TempDir Path logs) {
        FeatureSnapshot strict = FeatureResolver.fromProperties(Map.of("awms.features.preset", "standard",
                "awms.journal.required", "true"));
        StartupProfileAuditor auditor = auditor(brokenJournal(strict), strict, logs);

        assertThatThrownBy(() -> auditor.run(null))
                .isInstanceOf(FeatureConfigurationException.class)
                .hasMessageContaining("awms.journal.required=true")
                .hasMessageContaining("нет прав на sys_feature_log");
        auditor.destroy();
    }

    // ===================== Журнал доступа =====================

    @Test
    void repeatsAreGluedBeforeTouchingDatabase() {
        AtomicInteger inserts = new AtomicInteger();
        MovableClock clock = new MovableClock();
        JpaAccessAudit audit = new JpaAccessAudit(e -> inserts.incrementAndGet(), null, () -> false, clock);
        signIn("director_test", "DIRECTOR");

        for (int i = 0; i < 5; i++) {
            audit.record(AccessAudit.Event.VIDEO_VIEWED, Feature.VIDEO_ARCHIVE, "15", Map.of("range", true));
            audit.record(AccessAudit.Event.VIDEO_LIVE_OPENED, Feature.VIDEO_LIVE, "1", Map.of("mode", "snapshot"));
        }
        assertThat(inserts).hasValue(2);
        clock.advanceMinutes(11);
        audit.record(AccessAudit.Event.VIDEO_VIEWED, Feature.VIDEO_ARCHIVE, "15", Map.of("range", true));
        assertThat(inserts).hasValue(3);
    }

    @Test
    void failedWriteIsWarnedAndActionGoesOnByDefault() {
        AtomicBoolean broken = new AtomicBoolean(true);
        List<AccessLogEntry> rows = new ArrayList<>();
        JpaAccessAudit audit = new JpaAccessAudit(e -> {
            if (broken.get()) throw new IllegalStateException("БД недоступна");
            rows.add(e);
        }, null, () -> false, new MovableClock());
        signIn("director_test", "DIRECTOR");

        assertThatCode(() -> audit.record(AccessAudit.Event.VIDEO_LIVE_OPENED, Feature.VIDEO_LIVE, "1",
                Map.of("mode", "live"))).doesNotThrowAnyException();
        assertThat(securityLog.messages()).anySatisfy(m -> assertThat(m).contains("VIDEO_LIVE_OPENED")
                .contains("не записано").contains("БД недоступна").contains("awms.journal.required"));

        // Не записанное событие не занимает окно склейки: следующий запрос пишется
        assertThatCode(() -> audit.record(AccessAudit.Event.VIDEO_VIEWED, Feature.VIDEO_ARCHIVE, "15", null))
                .doesNotThrowAnyException();
        broken.set(false);
        audit.record(AccessAudit.Event.VIDEO_VIEWED, Feature.VIDEO_ARCHIVE, "15", null);
        assertThat(rows).singleElement().satisfies(e -> {
            assertThat(e.getEventType()).isEqualTo("VIDEO_VIEWED");
            assertThat(e.getActor()).isEqualTo("director_test");
        });
    }

    @Test
    void failedWriteDeniesActionWhenJournalRequired() {
        JpaAccessAudit audit = new JpaAccessAudit(e -> {
            throw new IllegalStateException("БД недоступна");
        }, null, () -> true, new MovableClock());
        assertThatThrownBy(() -> audit.record(AccessAudit.Event.VIDEO_LIVE_OPENED, Feature.VIDEO_LIVE, "1",
                Map.of("mode", "live")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("awms.journal.required")
                .hasRootCauseMessage("БД недоступна")
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(503));
    }

    @Test
    void ownEntityManagerCommitsOrRollsBackAndAlwaysCloses() {
        EntityManagerFactory factory = mock(EntityManagerFactory.class);
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(factory.createEntityManager()).thenReturn(em);
        when(em.getTransaction()).thenReturn(tx);
        JpaAccessAudit.Writer writer = JpaAccessAudit.ownTransaction(factory);

        AccessLogEntry entry = new AccessLogEntry();
        writer.insert(entry);
        var order = inOrder(tx, em);
        order.verify(tx).begin();
        order.verify(em).persist(entry);
        order.verify(tx).commit();
        order.verify(em).close();

        EntityManager failing = mock(EntityManager.class);
        EntityTransaction failingTx = mock(EntityTransaction.class);
        when(factory.createEntityManager()).thenReturn(failing);
        when(failing.getTransaction()).thenReturn(failingTx);
        when(failingTx.isActive()).thenReturn(true);
        doThrow(new IllegalStateException("триггер")).when(failing).persist(any());
        assertThatThrownBy(() -> writer.insert(new AccessLogEntry())).hasMessageContaining("триггер");
        verify(failingTx).rollback();
        verify(failing).close();
    }

    // ===================== OUTBOUND_BLOCKED =====================

    @Test
    void outboundBlockedGoesToProfileJournalOncePerWindowAndNeverThrows() {
        List<String> journal = new ArrayList<>();
        AtomicBoolean broken = new AtomicBoolean(false);
        MovableClock clock = new MovableClock();
        JpaAccessAudit audit = new JpaAccessAudit(e -> { }, (feature, target, reason) -> {
            if (broken.get()) throw new IllegalStateException("журнал недоступен");
            journal.add(feature.key() + " " + target + " " + reason);
        }, () -> true, clock);

        for (int i = 0; i < 3; i++) audit.outboundBlocked(Feature.ORDERS, "http://evil.example/v1", "нет в списке");
        audit.outboundBlocked(Feature.ORDERS, "http://other.example/v1", "нет в списке");
        assertThat(journal).containsExactly("orders http://evil.example/v1 нет в списке",
                "orders http://other.example/v1 нет в списке");

        clock.advanceMinutes(11);
        broken.set(true);
        // Запрос и так не уходит: сбой журнала профиля ничего не меняет, даже в строгом режиме
        assertThatCode(() -> audit.outboundBlocked(Feature.ORDERS, "http://evil.example/v1", "нет в списке"))
                .doesNotThrowAnyException();
        broken.set(false);
        audit.outboundBlocked(Feature.ORDERS, "http://evil.example/v1", "нет в списке");
        assertThat(journal).hasSize(3);
    }

    // ===================== Вспомогательное =====================

    /** Журнал, у которого каждая запись падает: рабочая роль БД без прав на журнал */
    private static FeatureJournal brokenJournal(FeatureSnapshot ceiling) {
        FeatureLogRepository rows = mock(FeatureLogRepository.class);
        when(rows.save(any())).thenThrow(new IllegalStateException("нет прав на sys_feature_log"));
        when(rows.nextId()).thenReturn(1L);
        return new FeatureJournal(rows, new FeatureService(ceiling), new JournalHmac(null), "test-host", "test",
                Clock.fixed(Instant.parse("2026-10-21T05:00:00Z"), ZoneId.of("Asia/Yekaterinburg")));
    }

    private static StartupProfileAuditor auditor(FeatureJournal journal, FeatureSnapshot ceiling, Path logs) {
        @SuppressWarnings("unchecked")
        ObjectProvider<FilterRegistrationBean<FeatureGateFilter>> none = mock(ObjectProvider.class);
        return new StartupProfileAuditor(journal, new FeatureService(ceiling), logs, none, new MockEnvironment());
    }

    private static void signIn(String login, String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(login,
                null, List.of(new SimpleGrantedAuthority(role))));
    }

    /** Часы, которые тест переводит вперёд */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-21T05:00:00Z");

        void advanceMinutes(long minutes) {
            now = now.plusSeconds(minutes * 60);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Yekaterinburg");
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
