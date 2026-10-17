// DocMoveOrderRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DocMoveOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocMoveOrderRepository extends JpaRepository<DocMoveOrder, UUID> {

    @Query("SELECT COALESCE(MAX(d.code), 0) FROM DocMoveOrder d")
    Integer findMaxCode();

    @Query("SELECT d FROM DocMoveOrder d WHERE d.uid = :uid")
    Optional<DocMoveOrder> findByUid(UUID uid);
}