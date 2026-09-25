package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DocStockLevelControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocStockLevelControlRepository extends JpaRepository<DocStockLevelControl, UUID> {

    @Query("SELECT d FROM DocStockLevelControl d " +
           "LEFT JOIN FETCH d.station s " +
           "ORDER BY d.code DESC")
    List<DocStockLevelControl> findAllWithRelations();

    @Query("SELECT d FROM DocStockLevelControl d " +
           "LEFT JOIN FETCH d.station s " +
           "WHERE d.uid = :uid")
    Optional<DocStockLevelControl> findByUidWithRelations(UUID uid);

    @Query("SELECT MAX(d.code) FROM DocStockLevelControl d")
    Integer findMaxCode();
}