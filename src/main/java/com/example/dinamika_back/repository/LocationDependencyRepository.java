package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.Location;
import com.example.dinamika_back.model.LocationDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationDependencyRepository extends JpaRepository<LocationDependency, Integer> {
    Optional<LocationDependency> findByChildLocation(Location childLocation);
    Optional<LocationDependency> findByChildLocationId(Integer childLocationId);
    List<LocationDependency> findByParentLocation(Location parentLocation);
    List<LocationDependency> findByParentLocationId(Integer parentLocationId);
    boolean existsByChildLocation(Location childLocation);
    boolean existsByChildLocationId(Integer childLocationId);
    boolean existsByParentLocationId(Integer parentLocationId);
    void deleteByChildLocation(Location childLocation);
}