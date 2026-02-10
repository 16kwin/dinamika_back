package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StationPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StationPositionRepository extends JpaRepository<StationPosition, Integer> {
    List<StationPosition> findByLocationId(Integer locationId);
    List<StationPosition> findByStationId(Integer stationId);
    
    Optional<StationPosition> findByStationIdAndLocationId(Integer stationId, Integer locationId);
    
    void deleteByStationIdAndLocationId(Integer stationId, Integer locationId);
    
    @Modifying
    @Query("DELETE FROM StationPosition sp WHERE sp.stationId = :stationId")
    void deleteByStationId(@Param("stationId") Integer stationId);
    
    @Modifying
    @Query("DELETE FROM StationPosition sp WHERE sp.locationId = :locationId")
    void deleteByLocationId(@Param("locationId") Integer locationId);
}