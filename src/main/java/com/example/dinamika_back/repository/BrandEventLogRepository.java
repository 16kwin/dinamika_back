package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.BrandEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BrandEventLogRepository extends JpaRepository<BrandEventLog, UUID> {
    List<BrandEventLog> findByBrandUidOrderByCreatedAtDesc(UUID brandUid);
    List<BrandEventLog> findAllByOrderByCreatedAtDesc();
}