package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StockLevelControlEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockLevelControlEventLogRepository extends JpaRepository<StockLevelControlEventLog, UUID> {

    List<StockLevelControlEventLog> findByDocUidOrderByCreatedAtDesc(UUID docUid);

    List<StockLevelControlEventLog> findAllByOrderByCreatedAtDesc();
}