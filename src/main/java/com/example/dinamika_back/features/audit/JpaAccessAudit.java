package com.example.dinamika_back.features.audit;

import com.example.dinamika_back.features.AccessAudit;
import com.example.dinamika_back.features.CeilingParam;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.features.journal.FeatureJournal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Журнал доступа в БД (раздел 10.2 проекта) — реализация {@link AccessAudit} вместо пустой {@link AccessAudit.Noop}.
 * Автор и адрес клиента — из текущего запроса ({@link FeatureJournal#currentActor()}), вне запроса — учётная
 * запись ОС службы.
 *
 * <ul>
 *   <li>Запись — своим EntityManager со своей транзакцией и своим соединением, а не через Spring-транзакцию потока
 *       запроса: строка не пропадёт при откате вызывающего, не зависит от его режима «только чтение» и не держит
 *       соединение с БД после вставки. Это важно для живого просмотра: ответ потока длится, пока зритель смотрит, и
 *       соединение, взятое в потоке запроса (open-in-view), висело бы весь просмотр — десяток зрителей занял бы пул.</li>
 *   <li>Повторы склеиваются до обращения к БД: запросы {@code Range} одного файла одним пользователем и кадры живого
 *       просмотра одной камеры дают одну запись на 10 минут.</li>
 *   <li>Сбой записи — по умолчанию предупреждение в лог {@code AWMS.SECURITY}, действие выполняется (как до
 *       выключателей). С {@code awms.journal.required=true} в потолке бросается 503: сторож отказывает в доступе,
 *       если просмотр не попал в журнал ({@code LiveStreamService.subscribe}), смена камеры откатывается.</li>
 *   <li>{@code OUTBOUND_BLOCKED} от сторожа исходящих уходит в журнал профиля в отдельной транзакции (вызывающий
 *       обычно откатывается после отказа), та же причина к тому же адресу — не чаще раза в 10 минут.</li>
 * </ul>
 */
@Component
@Primary
public class JpaAccessAudit implements AccessAudit {

    /** Окно склейки повторов */
    static final Duration REPEAT_WINDOW = Duration.ofMinutes(10);
    /** Предел ключей склейки в памяти: перебор файлов не раздует карту */
    private static final int REPEAT_KEYS_MAX = 10_000;
    /** Чтение одних и тех же данных порциями: просмотр видео (Range), скачивание, кадры живого просмотра */
    private static final Set<Event> REPEATABLE = Set.of(Event.VIDEO_VIEWED, Event.VIDEO_DOWNLOADED);
    private static final int DETAILS_MAX = 4_000;
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Вставка строки журнала доступа */
    @FunctionalInterface
    public interface Writer {
        void insert(AccessLogEntry entry);
    }

    /** Запись OUTBOUND_BLOCKED в журнал профиля */
    @FunctionalInterface
    public interface BlockedJournal {
        void outboundBlocked(Feature feature, String target, String reason);
    }

    private final Writer writer;
    private final BlockedJournal blockedJournal;
    private final BooleanSupplier required;
    private final Clock clock;
    private final Map<String, Long> recent = new ConcurrentHashMap<>();

    @Autowired
    public JpaAccessAudit(EntityManagerFactory entityManagerFactory, FeatureService features,
                          ObjectProvider<FeatureJournal> journal, PlatformTransactionManager transactions) {
        this(ownTransaction(entityManagerFactory), journalInNewTransaction(journal, transactions),
                () -> features.ceiling().boolParam(CeilingParam.JOURNAL_REQUIRED), Clock.systemDefaultZone());
    }

    /** Для тестов: журнал над репозиторием в памяти и своими часами; сбой записи не бросается */
    public JpaAccessAudit(AccessLogRepository repository, Clock clock) {
        this(repository::save, null, () -> false, clock);
    }

    /** Для тестов: своя вставка, журнал профиля, режим «журналы обязательны» и часы */
    public JpaAccessAudit(Writer writer, BlockedJournal blockedJournal, BooleanSupplier required, Clock clock) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.blockedJournal = blockedJournal;
        this.required = required;
        this.clock = clock;
    }

    @Override
    public void record(Event event, Feature feature, String objectRef, Map<String, ?> details) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(feature, "feature");
        FeatureJournal.Actor actor = FeatureJournal.currentActor();
        String repeatKey = repeatKey(event, actor, objectRef, details);
        long now = clock.millis();
        // Повтор в окне — без обращения к БД
        if (repeatKey != null && !reserve(repeatKey, now)) return;
        AccessLogEntry entry = new AccessLogEntry();
        entry.setOccurredAt(LocalDateTime.now(clock));
        entry.setEventType(event.name());
        entry.setFeatureKey(feature.key());
        entry.setActor(clip(actor.login(), 255));
        entry.setActorRole(clip(actor.role(), 64));
        entry.setClientIp(clip(actor.ip(), 64));
        entry.setObjectRef(clip(objectRef, 255));
        entry.setDetails(json(details));
        try {
            writer.insert(entry);
        } catch (RuntimeException e) {
            if (repeatKey != null) recent.remove(repeatKey, now);
            if (required.getAsBoolean()) {
                FeatureJournal.SECURITY.warn("Журнал доступа: событие {} ({}, {}) не записано — действие отклонено "
                        + "(awms.journal.required): {}", event, feature.key(), actor.login(), e.getMessage());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Журнал доступа недоступен: действие "
                        + "не выполнено, пока его нельзя записать (awms.journal.required)", e);
            }
            FeatureJournal.SECURITY.warn("Журнал доступа: событие {} ({}, объект {}, {}) не записано: {}. Действие "
                    + "выполнено: awms.journal.required в потолке не задан", event, feature.key(), clip(objectRef, 64),
                    actor.login(), e.getMessage());
        }
    }

    @Override
    public void outboundBlocked(Feature feature, String target, String reason) {
        if (blockedJournal == null) return;
        String key = "OUTBOUND_BLOCKED|" + (feature == null ? "-" : feature.key()) + "|" + target + "|" + reason;
        long now = clock.millis();
        if (!reserve(key, now)) return;
        try {
            blockedJournal.outboundBlocked(feature, target, reason);
        } catch (RuntimeException e) {
            recent.remove(key, now);
            // Запрос всё равно не ушёл; блокировка уже записана предупреждением сторожа
            FeatureJournal.SECURITY.warn("Журнал профиля: OUTBOUND_BLOCKED ({}) не записан: {}", target, e.getMessage());
        }
    }

    /** Ключ склейки повторов или null — каждое событие пишется */
    static String repeatKey(Event event, FeatureJournal.Actor actor, String objectRef, Map<String, ?> details) {
        boolean snapshot = event == Event.VIDEO_LIVE_OPENED && details != null
                && "snapshot".equals(String.valueOf(details.get("mode")));
        if (!REPEATABLE.contains(event) && !snapshot) return null;
        return event + "|" + actor.login() + "|" + objectRef + (snapshot ? "|snapshot" : "");
    }

    /** Занять ключ на окно склейки: false — такое же событие уже было в окне (или пишется сейчас) */
    private boolean reserve(String key, long now) {
        if (recent.size() >= REPEAT_KEYS_MAX) {
            recent.entrySet().removeIf(e -> now - e.getValue() >= REPEAT_WINDOW.toMillis());
            if (recent.size() >= REPEAT_KEYS_MAX) recent.clear();
        }
        boolean[] fresh = {false};
        recent.compute(key, (k, last) -> {
            if (last != null && now - last < REPEAT_WINDOW.toMillis()) return last;
            fresh[0] = true;
            return now;
        });
        return fresh[0];
    }

    /** Вставка своим EntityManager: своя транзакция и своё соединение, закрываются сразу после вставки */
    public static Writer ownTransaction(EntityManagerFactory entityManagerFactory) {
        return entry -> {
            EntityManager em = entityManagerFactory.createEntityManager();
            try {
                EntityTransaction tx = em.getTransaction();
                tx.begin();
                try {
                    em.persist(entry);
                    tx.commit();
                } catch (RuntimeException e) {
                    if (tx.isActive()) tx.rollback();
                    throw e;
                }
            } finally {
                em.close();
            }
        };
    }

    /** OUTBOUND_BLOCKED в журнал профиля в новой транзакции: откат вызывающего запись не уносит */
    static BlockedJournal journalInNewTransaction(ObjectProvider<FeatureJournal> journal,
                                                  PlatformTransactionManager transactions) {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return (feature, target, reason) -> {
            FeatureJournal profile = journal.getIfAvailable();
            if (profile == null) return;
            template.executeWithoutResult(status -> profile.outboundBlocked(feature, target, reason));
        };
    }

    static String json(Map<String, ?> details) {
        if (details == null || details.isEmpty()) return null;
        try {
            String text = JSON.writeValueAsString(new TreeMap<>(details));
            if (text.length() > DETAILS_MAX) text = JSON.writeValueAsString(Map.of("truncated", true, "length", text.length()));
            return text.replace("\0", "");
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String clip(String value, int max) {
        if (value == null) return null;
        String text = value.replace("\0", "");
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
