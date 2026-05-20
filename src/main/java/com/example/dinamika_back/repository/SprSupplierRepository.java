package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprSupplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Репозиторий для работы со справочником поставщиков */
@Repository
public interface SprSupplierRepository extends JpaRepository<SprSupplier, UUID> {
}