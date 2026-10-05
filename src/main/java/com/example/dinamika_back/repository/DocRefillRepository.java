package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DocRefill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocRefillRepository extends JpaRepository<DocRefill, UUID> {

    @Query("SELECT DISTINCT d FROM DocRefill d " +
           "LEFT JOIN FETCH d.template " +
           "LEFT JOIN FETCH d.createdBy " +
           "ORDER BY d.docDatetime DESC")
    List<DocRefill> findAllWithRelations();

    @Query("SELECT d FROM DocRefill d " +
           "LEFT JOIN FETCH d.template " +
           "LEFT JOIN FETCH d.createdBy " +
           "WHERE d.uid = :uid")
    Optional<DocRefill> findByUidWithRelations(UUID uid);

    @Query("SELECT COALESCE(MAX(d.code), 0) FROM DocRefill d")
    Integer findMaxCode();
}