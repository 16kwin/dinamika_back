package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.ProductionDirectionEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductionDirectionEventLogRepository extends JpaRepository<ProductionDirectionEventLog, UUID> {
    List<ProductionDirectionEventLog> findByProductionDirectionUidOrderByCreatedAtDesc(UUID productionDirectionUid);
    List<ProductionDirectionEventLog> findAllByOrderByCreatedAtDesc();
}