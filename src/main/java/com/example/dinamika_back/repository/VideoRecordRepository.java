package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.VideoRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRecordRepository extends JpaRepository<VideoRecord, Long> {

    /** Журнал за период, свежие записи сверху */
    @Query("SELECT r FROM VideoRecord r WHERE r.startedAt >= :dateFrom AND r.startedAt < :dateTo "
            + "ORDER BY r.startedAt DESC, r.id DESC")
    List<VideoRecord> findByPeriod(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    /** Записи в заданном статусе — для восстановления после перезапуска сервера */
    List<VideoRecord> findByStatus(String status);

    /** Открытая запись события на станции — её закрывает событие «окончание» */
    Optional<VideoRecord> findFirstByStationIdAndEventTypeAndStatusOrderByStartedAtDesc(
            Long stationId, String eventType, String status);
}
