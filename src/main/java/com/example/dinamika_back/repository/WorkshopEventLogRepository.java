// WorkshopEventLogRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.WorkshopEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkshopEventLogRepository extends JpaRepository<WorkshopEventLog, UUID> {
    List<WorkshopEventLog> findByWorkshopIdOrderByCreatedAtDesc(Long workshopId);
    List<WorkshopEventLog> findAllByOrderByCreatedAtDesc();
}