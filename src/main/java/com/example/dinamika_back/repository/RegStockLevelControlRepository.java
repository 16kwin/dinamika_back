package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegStockLevelControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegStockLevelControlRepository extends JpaRepository<RegStockLevelControl, UUID> {

    @Query("SELECT r FROM RegStockLevelControl r " +
           "WHERE r.station.uid = :stationUid AND r.material.uid = :materialUid")
    Optional<RegStockLevelControl> findByStationUidAndMaterialUid(String stationUid, UUID materialUid);

    @Query("SELECT r FROM RegStockLevelControl r " +
           "LEFT JOIN FETCH r.material m " +
           "LEFT JOIN FETCH r.station s " +
           "WHERE r.station.uid = :stationUid " +
           "ORDER BY m.nameMaterial ASC")
    List<RegStockLevelControl> findByStationUid(String stationUid);
}