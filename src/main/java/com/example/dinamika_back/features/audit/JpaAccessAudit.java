package com.example.dinamika_back.features.audit;

import com.example.dinamika_back.features.AccessAudit;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.journal.FeatureJournal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Журнал доступа в БД (раздел 10.2 проекта) — реализация {@link AccessAudit} вместо пустой {@link AccessAudit.Noop}.
 * Автор и адрес клиента — из текущего запроса ({@link FeatureJournal#currentActor()}), вне запроса — учётная
 * запись ОС службы.
 *
 * <ul>
 *   <li>Своя транзакция: запись не пропадёт при откате вызывающего и не зависит от его режима «только чтение».</li>
 *   <li>Ошибка записи не глотается: сторож может отказать в доступе, если просмотр не попал в журнал
 *       ({@code LiveStreamService.subscribe}).</li>
 *   <li>Повторы: запросы {@code Range} одного файла одним пользователем и кадры живого просмотра одной камеры
 *       дают одну запись на 10 минут.</li>
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
    private static final Logger log = LoggerFactory.getLogger(JpaAccessAudit.class);

    private final AccessLogRepository repository;
    private final Clock clock;
    private final Map<String, Long> recent = new ConcurrentHashMap<>();

    @Autowired
    public JpaAccessAudit(AccessLogRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    /** Для тестов: свои часы */
    public JpaAccessAudit(AccessLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Event event, Feature feature, String objectRef, Map<String, ?> details) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(feature, "feature");
        FeatureJournal.Actor actor = FeatureJournal.currentActor();
        String repeatKey = repeatKey(event, actor, objectRef, details);
        long now = clock.millis();
        if (repeatKey != null) {
            Long last = recent.get(repeatKey);
            if (last != null && now - last < REPEAT_WINDOW.toMillis()) return;
        }
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
            repository.save(entry);
        } catch (RuntimeException e) {
            log.warn("Журнал доступа: событие {} ({}) не записано: {}", event, feature.key(), e.getMessage());
            throw e;
        }
        if (repeatKey != null) remember(repeatKey, now);
    }

    /** Ключ склейки повторов или null — каждое событие пишется */
    static String repeatKey(Event event, FeatureJournal.Actor actor, String objectRef, Map<String, ?> details) {
        boolean snapshot = event == Event.VIDEO_LIVE_OPENED && details != null
                && "snapshot".equals(String.valueOf(details.get("mode")));
        if (!REPEATABLE.contains(event) && !snapshot) return null;
        return event + "|" + actor.login() + "|" + objectRef + (snapshot ? "|snapshot" : "");
    }

    private void remember(String key, long now) {
        if (recent.size() >= REPEAT_KEYS_MAX) {
            recent.entrySet().removeIf(e -> now - e.getValue() >= REPEAT_WINDOW.toMillis());
            if (recent.size() >= REPEAT_KEYS_MAX) recent.clear();
        }
        recent.put(key, now);
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
