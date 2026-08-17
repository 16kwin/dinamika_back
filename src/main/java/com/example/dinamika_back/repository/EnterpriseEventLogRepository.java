// EnterpriseEventLogRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.EnterpriseEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EnterpriseEventLogRepository extends JpaRepository<EnterpriseEventLog, UUID> {
    List<EnterpriseEventLog> findByEnterpriseIdOrderByCreatedAtDesc(Long enterpriseId);
    List<EnterpriseEventLog> findAllByOrderByCreatedAtDesc();
}