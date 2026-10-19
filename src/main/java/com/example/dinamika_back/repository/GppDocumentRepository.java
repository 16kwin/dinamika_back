package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.GppDocument;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GppDocumentRepository extends JpaRepository<GppDocument, UUID> {

    /** Документ предприятия (он один; если вдруг несколько — самый ранний) */
    Optional<GppDocument> findFirstByOrderByCreatedAtAsc();

    /**
     * То же с блокировкой строки до конца транзакции — сохранения идут по очереди, и версия
     * для проверки читается уже под блокировкой. Вызывать первым обращением к документу в транзакции.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GppDocument> findFirstForUpdateByOrderByCreatedAtAsc();

    /**
     * Сколько ждать чужую блокировку до конца текущей транзакции (PostgreSQL lock_timeout, например '10s').
     * Подсказка JPA lock.timeout на PostgreSQL не работает: FOR UPDATE там не принимает время ожидания.
     */
    @Query(value = "SELECT set_config('lock_timeout', :timeout, true)", nativeQuery = true)
    String setLockTimeout(@Param("timeout") String timeout);
}
