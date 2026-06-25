// StationRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StationRepository extends JpaRepository<Station, Long>, JpaSpecificationExecutor<Station> {
    
    @Query("SELECT s FROM Station s")
    List<Station> findAllStaticData();
    
    Optional<Station> findByUid(String uid);
    
    @Query("SELECT s FROM Station s WHERE s.activeTemplate.uid = :templateUid")
    List<Station> findByActiveTemplateUid(@Param("templateUid") UUID templateUid);
}