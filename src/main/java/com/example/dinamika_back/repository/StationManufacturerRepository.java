// StationManufacturerRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StationManufacturer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StationManufacturerRepository extends JpaRepository<StationManufacturer, UUID> {
    Optional<StationManufacturer> findByName(String name);
    boolean existsByName(String name);
}