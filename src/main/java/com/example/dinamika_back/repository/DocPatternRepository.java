package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DocPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Репозиторий для работы с шаблонами пополнения станций */
@Repository
public interface DocPatternRepository extends JpaRepository<DocPattern, UUID> {
    
    /** Найти все шаблоны для конкретной станции */
    List<DocPattern> findByStationUid(String stationUid);
    
    /** Найти активные шаблоны */
    List<DocPattern> findByStatusDocTrue();
}