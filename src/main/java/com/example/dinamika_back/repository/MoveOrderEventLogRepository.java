// MoveOrderEventLogRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.MoveOrderEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MoveOrderEventLogRepository extends JpaRepository<MoveOrderEventLog, UUID> {

    List<MoveOrderEventLog> findByDocUidOrderByCreatedAtDesc(UUID docUid);

    List<MoveOrderEventLog> findAllByOrderByCreatedAtDesc();
}