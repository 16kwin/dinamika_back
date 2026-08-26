package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SupplierBrandEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupplierBrandEventLogRepository extends JpaRepository<SupplierBrandEventLog, UUID> {
    List<SupplierBrandEventLog> findBySupplierBrandUidOrderByCreatedAtDesc(UUID supplierBrandUid);
    List<SupplierBrandEventLog> findAllByOrderByCreatedAtDesc();
}