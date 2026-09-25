package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.TemplateEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TemplateEventLogRepository extends JpaRepository<TemplateEventLog, UUID> {
    List<TemplateEventLog> findByTemplateUidOrderByCreatedAtDesc(UUID templateUid);
    List<TemplateEventLog> findAllByOrderByCreatedAtDesc();
}