package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprTypeMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Репозиторий для справочника "Группы учета" (ТМЦ, Готовые детали и т.д.) */
@Repository
public interface SprTypeMaterialRepository extends JpaRepository<SprTypeMaterial, UUID> {
    // JpaRepository уже даёт findAll(), findById() и т.д.
}