package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegCells;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Репозиторий для работы с регистром привязки ячеек к шаблонам */
@Repository
public interface RegCellsRepository extends JpaRepository<RegCells, UUID> {
    
    /** Найти все ячейки шаблона */
    List<RegCells> findByDocPatternUid(UUID docPatternUid);
    
    /** Найти ячейку по номеру в рамках шаблона */
    List<RegCells> findByDocPatternUidAndNumberCell(UUID docPatternUid, Integer numberCell);
}