// HoldingRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    Optional<Holding> findByName(String name);

    boolean existsByName(String name);

    List<Holding> findAllByOrderByNameAsc();

    @Query("SELECT h FROM Holding h LEFT JOIN FETCH h.enterprises e LEFT JOIN FETCH e.workshops w LEFT JOIN FETCH w.sections WHERE h.id = ?1")
    Optional<Holding> findByIdWithHierarchy(Long id);

    @Query("SELECT DISTINCT h FROM Holding h LEFT JOIN FETCH h.enterprises e LEFT JOIN FETCH e.workshops w LEFT JOIN FETCH w.sections ORDER BY h.name ASC")
    Set<Holding> findAllWithHierarchy();
}