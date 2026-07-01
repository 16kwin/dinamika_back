// StationModelRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StationModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StationModelRepository extends JpaRepository<StationModel, UUID> {

    @Query("SELECT COALESCE(MAX(m.code), 0) FROM StationModel m")
    Integer findMaxCode();

    Optional<StationModel> findByName(String name);
    boolean existsByName(String name);
    boolean existsByCode(Integer code);
}