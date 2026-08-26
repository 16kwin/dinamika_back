package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.ModelEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ModelEventLogRepository extends JpaRepository<ModelEventLog, UUID> {
    List<ModelEventLog> findByModelUidOrderByCreatedAtDesc(UUID modelUid);
    List<ModelEventLog> findAllByOrderByCreatedAtDesc();
}