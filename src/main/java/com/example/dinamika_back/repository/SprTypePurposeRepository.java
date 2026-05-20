package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprTypePurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Репозиторий для работы со справочником типов назначения материалов */
@Repository
public interface SprTypePurposeRepository extends JpaRepository<SprTypePurpose, UUID> {
}