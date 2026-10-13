package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RefillEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefillEventLogRepository extends JpaRepository<RefillEventLog, UUID> {
    List<RefillEventLog> findByDocRefillUidOrderByCreatedAtDesc(UUID docRefillUid);
    List<RefillEventLog> findAllByOrderByCreatedAtDesc();
}