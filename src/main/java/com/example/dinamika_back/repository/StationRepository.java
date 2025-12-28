package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StationRepository extends JpaRepository<Station, Integer> {
    List<Station> findByStationNameContainingIgnoreCase(String stationName);
    List<Station> findByStationModelId(Integer stationModelId);
    List<Station> findByIpAddress(String ipAddress);
    boolean existsBySerialNumberOfTheStation(Integer serialNumber);
    
    @Query("SELECT s FROM Station s WHERE s.level1Factory.id = :locationId AND s.level2Object IS NULL AND s.level3Zone IS NULL")
    List<Station> findStationsDirectlyUnderLocation(@Param("locationId") Integer locationId);
    
    @Query("SELECT s FROM Station s WHERE s.level2Object IS NOT NULL AND s.level2Object.id = :locationId AND s.level3Zone IS NULL")
    List<Station> findStationsUnderLevel2Location(@Param("locationId") Integer locationId);
    
    @Query("SELECT s FROM Station s WHERE s.level3Zone IS NOT NULL AND s.level3Zone.id = :locationId")
    List<Station> findStationsUnderLevel3Location(@Param("locationId") Integer locationId);
}