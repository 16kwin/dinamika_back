package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.ManufacturerEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ManufacturerEventLogRepository extends JpaRepository<ManufacturerEventLog, UUID> {
    List<ManufacturerEventLog> findByManufacturerUidOrderByCreatedAtDesc(UUID manufacturerUid);
    List<ManufacturerEventLog> findAllByOrderByCreatedAtDesc();
}