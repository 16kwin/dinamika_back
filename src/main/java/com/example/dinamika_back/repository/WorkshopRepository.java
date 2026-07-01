// WorkshopRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.Workshop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface WorkshopRepository extends JpaRepository<Workshop, Long> {
    
    List<Workshop> findByEnterpriseId(Long enterpriseId);
    
    List<Workshop> findByEnterpriseIdOrderByNameAsc(Long enterpriseId);
    
    Optional<Workshop> findByNameAndEnterpriseId(String name, Long enterpriseId);
    
    boolean existsByNameAndEnterpriseId(String name, Long enterpriseId);
    
    @Query("SELECT w FROM Workshop w LEFT JOIN FETCH w.sections WHERE w.enterprise.id = ?1 ORDER BY w.name ASC")
    Set<Workshop> findByEnterpriseIdWithSections(Long enterpriseId);
}