// StationTypeRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StationTypeRepository extends JpaRepository<StationType, UUID> {
    Optional<StationType> findByName(String name);
    boolean existsByName(String name);
}