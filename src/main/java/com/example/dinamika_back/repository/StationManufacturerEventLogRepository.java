// StationManufacturerEventLogRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StationManufacturerEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StationManufacturerEventLogRepository extends JpaRepository<StationManufacturerEventLog, UUID> {
    List<StationManufacturerEventLog> findByStationManufacturerUidOrderByCreatedAtDesc(UUID stationManufacturerUid);
    List<StationManufacturerEventLog> findAllByOrderByCreatedAtDesc();
}