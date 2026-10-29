package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.TransferEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TransferEventLogRepository extends JpaRepository<TransferEventLog, UUID> {
    List<TransferEventLog> findByDocUidOrderByCreatedAtDesc(UUID docUid);
    List<TransferEventLog> findAllByOrderByCreatedAtDesc();
}