// StationManufacturerRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StationManufacturer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StationManufacturerRepository extends JpaRepository<StationManufacturer, UUID> {

    Optional<StationManufacturer> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT m FROM StationManufacturer m WHERE m.country.uid = :countryUid")
    List<StationManufacturer> findByCountryUid(@Param("countryUid") UUID countryUid);
}