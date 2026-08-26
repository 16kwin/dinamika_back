package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.TypePurposeEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TypePurposeEventLogRepository extends JpaRepository<TypePurposeEventLog, UUID> {
    List<TypePurposeEventLog> findByTypePurposeUidOrderByCreatedAtDesc(UUID typePurposeUid);
    List<TypePurposeEventLog> findAllByOrderByCreatedAtDesc();
}