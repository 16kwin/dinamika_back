package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.MeasureEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MeasureEventLogRepository extends JpaRepository<MeasureEventLog, UUID> {
    List<MeasureEventLog> findByMeasureUidOrderByCreatedAtDesc(UUID measureUid);
    List<MeasureEventLog> findAllByOrderByCreatedAtDesc();
}