package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface RegEventLogRepository extends JpaRepository<RegEventLog, UUID> {
    List<RegEventLog> findByMaterialUidOrderByCreatedAtDesc(UUID materialUid);
    
    List<RegEventLog> findAllByOrderByCreatedAtDesc();
}