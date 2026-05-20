package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprTypeMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Репозиторий для работы со справочником видов хранимых материалов */
@Repository
public interface SprTypeMaterialRepository extends JpaRepository<SprTypeMaterial, UUID> {
}