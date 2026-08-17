// HoldingEventLogRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.HoldingEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HoldingEventLogRepository extends JpaRepository<HoldingEventLog, UUID> {
    List<HoldingEventLog> findByHoldingIdOrderByCreatedAtDesc(Long holdingId);
    List<HoldingEventLog> findAllByOrderByCreatedAtDesc();
}