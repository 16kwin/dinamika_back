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
    
    // Новые методы для агрегации данных Dashboard
    @Query("SELECT COUNT(s) FROM Station s")
    Long countAllStations();
    
    @Query("SELECT COALESCE(SUM(s.fullness), 0) FROM Station s") // ← Исправлено: fullness
    Long sumFullness(); // ← Исправлено: sumFullness
    
    @Query("SELECT COALESCE(SUM(s.capacity), 0) FROM Station s")
    Long sumCapacity();
    
    @Query("SELECT COALESCE(SUM(s.issued), 0) FROM Station s")
    Long sumIssued();
    
    @Query("SELECT COALESCE(SUM(s.issuedOverNorm), 0) FROM Station s")
    Long sumIssuedOverNorm();
    
    // Альтернативно, можно использовать один запрос с проекцией
    @Query("SELECT " +
           "COUNT(s) as totalStations, " +
           "COALESCE(SUM(s.fullness), 0) as totalFullness, " + // ← Изменено на fullness
           "COALESCE(SUM(s.capacity), 0) as totalCapacity, " +
           "COALESCE(SUM(s.issued), 0) as totalIssued, " +
           "COALESCE(SUM(s.issuedOverNorm), 0) as totalIssuedOverNorm " +
           "FROM Station s")
    Object[] getDashboardStats();
}