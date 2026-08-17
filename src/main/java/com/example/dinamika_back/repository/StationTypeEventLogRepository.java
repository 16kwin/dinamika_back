// StationTypeEventLogRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StationTypeEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StationTypeEventLogRepository extends JpaRepository<StationTypeEventLog, UUID> {
    List<StationTypeEventLog> findByStationTypeUidOrderByCreatedAtDesc(UUID stationTypeUid);
    List<StationTypeEventLog> findAllByOrderByCreatedAtDesc();
}