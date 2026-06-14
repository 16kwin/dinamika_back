package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprSupplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SprSupplierRepository extends JpaRepository<SprSupplier, UUID> {

    @Query("SELECT COALESCE(MAX(s.code), 0) FROM SprSupplier s")
    Integer findMaxCode();
}