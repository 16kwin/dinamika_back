package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.TypeAttributeEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TypeAttributeEventLogRepository extends JpaRepository<TypeAttributeEventLog, UUID> {
    List<TypeAttributeEventLog> findByTypeAttributeUidOrderByCreatedAtDesc(UUID typeAttributeUid);
    List<TypeAttributeEventLog> findAllByOrderByCreatedAtDesc();
}