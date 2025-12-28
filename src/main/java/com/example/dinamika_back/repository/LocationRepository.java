package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Integer> {
    List<Location> findByLocationNameContainingIgnoreCase(String locationName);
    List<Location> findByLevel(Integer level);
    Optional<Location> findByLocationName(String locationName);
    boolean existsByLocationName(String locationName);
    boolean existsByLevelAndLocationName(Integer level, String locationName);
    
    @Query("SELECT l FROM Location l WHERE l.level = 1 ORDER BY l.locationName")
    List<Location> findAllLevel1Locations();
    
    List<Location> findByLevelOrderByLocationName(Integer level);
}