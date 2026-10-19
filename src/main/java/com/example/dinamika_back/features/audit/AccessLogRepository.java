package com.example.dinamika_back.features.audit;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Журнал доступа {@code sys_access_log}: только вставка и чтение, методов изменения и удаления нет
 * (база их тоже отклоняет триггерами V18).
 */
public interface AccessLogRepository extends Repository<AccessLogEntry, Long> {

    AccessLogEntry save(AccessLogEntry entry);

    long count();

    /**
     * Сводка использования для отчёта (раздел 9.2, строка 13): строки {@code [actor, actor_role, event_type, число]}
     * за период с {@code since}
     */
    @Query("SELECT e.actor, e.actorRole, e.eventType, COUNT(e) FROM AccessLogEntry e WHERE e.occurredAt >= :since "
            + "GROUP BY e.actor, e.actorRole, e.eventType ORDER BY e.actor, e.eventType")
    List<Object[]> usageSince(@Param("since") LocalDateTime since);
}
