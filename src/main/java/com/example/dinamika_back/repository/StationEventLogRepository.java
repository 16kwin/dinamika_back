// StationEventLogRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StationEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StationEventLogRepository extends JpaRepository<StationEventLog, UUID> {
    List<StationEventLog> findByStationUidOrderByCreatedAtDesc(String stationUid);
    List<StationEventLog> findAllByOrderByCreatedAtDesc();
}