// RegSupplierEventLogRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegSupplierEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RegSupplierEventLogRepository extends JpaRepository<RegSupplierEventLog, UUID> {

    List<RegSupplierEventLog> findBySupplierUidOrderByCreatedAtDesc(UUID supplierUid);
}