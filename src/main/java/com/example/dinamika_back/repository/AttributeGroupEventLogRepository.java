package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.AttributeGroupEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttributeGroupEventLogRepository extends JpaRepository<AttributeGroupEventLog, UUID> {
    List<AttributeGroupEventLog> findByAttributeGroupUidOrderByCreatedAtDesc(UUID attributeGroupUid);
    List<AttributeGroupEventLog> findAllByOrderByCreatedAtDesc();
}