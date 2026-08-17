// StationModelEventLogRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StationModelEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StationModelEventLogRepository extends JpaRepository<StationModelEventLog, UUID> {
    List<StationModelEventLog> findByStationModelUidOrderByCreatedAtDesc(UUID stationModelUid);
    List<StationModelEventLog> findAllByOrderByCreatedAtDesc();
}