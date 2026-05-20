package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Репозиторий для работы со справочником брендов */
@Repository
public interface SprBrandRepository extends JpaRepository<SprBrand, UUID> {
}