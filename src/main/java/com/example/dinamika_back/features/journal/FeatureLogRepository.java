package com.example.dinamika_back.features.journal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Журнал профиля {@code sys_feature_log}. Только вставка и чтение: методов изменения и удаления нет
 * (база их тоже отклоняет триггерами V18). Базовый интерфейс — {@link Repository}, а не JpaRepository:
 * наружу открыто ровно то, что нужно журналу, и тесты подставляют свою реализацию в памяти.
 */
public interface FeatureLogRepository extends Repository<FeatureLogEntry, Long> {

    /** Вставка новой записи (запись {@code Persistable} с заранее заданным номером) */
    FeatureLogEntry save(FeatureLogEntry entry);

    Optional<FeatureLogEntry> findById(Long id);

    long count();

    /**
     * Блокировка цепочки до конца транзакции: записи журнала выстраиваются по одной, у каждой — свой предыдущий
     * хэш. Расчёт предполагает один экземпляр бэкенда; блокировка держит порядок и при нескольких
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:key)) AS l", nativeQuery = true)
    Integer lockChain(@Param("key") long key);

    /** Номер следующей записи: он входит в хэш строки, поэтому берётся до вставки */
    @Query(value = "SELECT nextval(pg_get_serial_sequence('sys_feature_log', 'id'))", nativeQuery = true)
    Long nextId();

    /** Голова цепочки — последняя запись */
    Optional<FeatureLogEntry> findTopByOrderByIdDesc();

    /** Последняя запись данного типа (последний STARTUP — для сравнения потолков) */
    Optional<FeatureLogEntry> findTopByEventTypeOrderByIdDesc(String eventType);

    /** Порция записей по возрастанию номера после заданного — для проверки цепочки */
    List<FeatureLogEntry> findByIdGreaterThanOrderByIdAsc(Long afterId, Pageable page);

    /** Последние записи — для отчёта */
    List<FeatureLogEntry> findByOrderByIdDesc(Pageable page);

    /** Последние записи данных типов (TAMPER, PURGE, RETENTION) — для отчёта */
    List<FeatureLogEntry> findByEventTypeInOrderByIdDesc(Collection<String> eventTypes, Pageable page);
}
