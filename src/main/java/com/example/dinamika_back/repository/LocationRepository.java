// LocationRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
    Optional<Location> findByName(String name);
    boolean existsByName(String name);
}