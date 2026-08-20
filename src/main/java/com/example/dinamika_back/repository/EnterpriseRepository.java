// EnterpriseRepository.java — ПОЛНЫЙ ФАЙЛ (добавлен findByLocationUid)
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.Enterprise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface EnterpriseRepository extends JpaRepository<Enterprise, Long> {

    Optional<Enterprise> findByName(String name);

    boolean existsByName(String name);

    List<Enterprise> findAllByOrderByNameAsc();

    List<Enterprise> findByHoldingIdOrderByNameAsc(Long holdingId);

    @Query("SELECT e FROM Enterprise e LEFT JOIN FETCH e.workshops w LEFT JOIN FETCH w.sections WHERE e.id = ?1")
    Optional<Enterprise> findByIdWithHierarchy(Long id);

    @Query("SELECT DISTINCT e FROM Enterprise e LEFT JOIN FETCH e.workshops w LEFT JOIN FETCH w.sections ORDER BY e.name ASC")
    Set<Enterprise> findAllWithHierarchy();

    @Query("SELECT DISTINCT e FROM Enterprise e LEFT JOIN FETCH e.workshops w LEFT JOIN FETCH w.sections WHERE e.holding.id = ?1 ORDER BY e.name ASC")
    Set<Enterprise> findByHoldingIdWithHierarchy(Long holdingId);

    @Query("SELECT e FROM Enterprise e WHERE e.location.uid = :locationUid")
    List<Enterprise> findByLocationUid(@Param("locationUid") UUID locationUid);
}