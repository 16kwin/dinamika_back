package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprManufacturer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Репозиторий для работы со справочником производителей */
@Repository
public interface SprManufacturerRepository extends JpaRepository<SprManufacturer, UUID> {
}