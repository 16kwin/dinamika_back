// SectionEventLogRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SectionEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SectionEventLogRepository extends JpaRepository<SectionEventLog, UUID> {
    List<SectionEventLog> findBySectionIdOrderByCreatedAtDesc(Long sectionId);
    List<SectionEventLog> findAllByOrderByCreatedAtDesc();
}