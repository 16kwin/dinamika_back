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
    
    @Query("SELECT DISTINCT s FROM Station s " +
           "LEFT JOIN FETCH s.model m " +
           "LEFT JOIN FETCH m.type " +
           "LEFT JOIN FETCH m.manufacturer " +
           "LEFT JOIN FETCH s.configuration " +
           "LEFT JOIN FETCH s.holding " +
           "LEFT JOIN FETCH s.enterprise " +
           "LEFT JOIN FETCH s.workshop " +
           "LEFT JOIN FETCH s.section " +
           "LEFT JOIN FETCH s.activeTemplate")
    List<Station> findAllWithRelations();
    
    // Проекция — только сама станция, без связей
    @Query("SELECT s FROM Station s")
    List<Station> findAllStationsOnly();
    
    // Проекция со связями модели
    @Query("SELECT DISTINCT s FROM Station s " +
           "LEFT JOIN FETCH s.model m " +
           "LEFT JOIN FETCH m.type " +
           "LEFT JOIN FETCH m.manufacturer")
    List<Station> findAllWithModel();
    
    // Проекция с моделью и конфигурацией
    @Query("SELECT DISTINCT s FROM Station s " +
           "LEFT JOIN FETCH s.model m " +
           "LEFT JOIN FETCH m.type " +
           "LEFT JOIN FETCH s.configuration")
    List<Station> findAllWithModelAndConfig();
    
    // Проекция с размещением
    @Query("SELECT DISTINCT s FROM Station s " +
           "LEFT JOIN FETCH s.holding " +
           "LEFT JOIN FETCH s.enterprise " +
           "LEFT JOIN FETCH s.workshop " +
           "LEFT JOIN FETCH s.section")
    List<Station> findAllWithPlacement();
    
    Optional<Station> findByUid(String uid);
    
    boolean existsByUid(String uid);
    
    @Query("SELECT s FROM Station s WHERE s.activeTemplate.uid = :templateUid")
    List<Station> findByActiveTemplateUid(@Param("templateUid") UUID templateUid);
    
    @Query("SELECT MAX(s.code) FROM Station s")
    Integer findMaxCode();
    
    boolean existsByCode(Integer code);
}