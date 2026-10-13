package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegRefillCell;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RegRefillCellRepository extends JpaRepository<RegRefillCell, UUID> {

    @Query("SELECT c FROM RegRefillCell c " +
           "LEFT JOIN FETCH c.cellAssignment " +
           "LEFT JOIN FETCH c.material " +
           "WHERE c.docRefill.uid = :docUid " +
           "ORDER BY c.drumNumber, c.columnNumber, c.numberCell")
    List<RegRefillCell> findByDocUid(UUID docUid);

    @Modifying
    @Query("DELETE FROM RegRefillCell c WHERE c.docRefill.uid = :docUid")
    void deleteByDocUid(UUID docUid);
}