package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprTypeProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Репозиторий для работы со справочником видов товара */
@Repository
public interface SprTypeProductRepository extends JpaRepository<SprTypeProduct, UUID> {
}