package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprManufacturer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SprManufacturerRepository extends JpaRepository<SprManufacturer, UUID> {
    
    List<SprManufacturer> findByDirectionUid(UUID directionUid);
    
    List<SprManufacturer> findByCountryUid(UUID countryUid);
    
    @Query("SELECT COALESCE(MAX(m.code), 0) FROM SprManufacturer m")
    Integer findMaxCode();
}