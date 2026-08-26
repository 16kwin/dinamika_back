package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SupplierDirectionEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupplierDirectionEventLogRepository extends JpaRepository<SupplierDirectionEventLog, UUID> {
    List<SupplierDirectionEventLog> findBySupplierDirectionUidOrderByCreatedAtDesc(UUID supplierDirectionUid);
    List<SupplierDirectionEventLog> findAllByOrderByCreatedAtDesc();
}