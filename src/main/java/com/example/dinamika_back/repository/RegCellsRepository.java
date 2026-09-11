package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegCells;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RegCellsRepository extends JpaRepository<RegCells, UUID> {

    List<RegCells> findByDocPatternUid(UUID docPatternUid);

    List<RegCells> findByDocPatternUidAndNumberCell(UUID docPatternUid, Integer numberCell);

    void deleteByDocPatternUid(UUID docPatternUid);
}