package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Репозиторий для работы со справочником номенклатуры (основной объект системы) */
@Repository
public interface SprMaterialRepository extends JpaRepository<SprMaterial, UUID> {
    
    /** Найти материал по артикулу */
    Optional<SprMaterial> findByArticle(String article);
    
    /** Найти материал по штрихкоду */
    Optional<SprMaterial> findByBarcode(String barcode);
    
    /** Поиск материалов по наименованию (содержит) */
    List<SprMaterial> findByNameMaterialContainingIgnoreCase(String name);
    
    /** Найти все материалы группы */
    List<SprMaterial> findByGroupMaterialUid(UUID groupUid);
    
    /** Найти все материалы производителя */
    List<SprMaterial> findByManufacturerUid(UUID manufacturerUid);
    
    /** Найти все материалы бренда */
    List<SprMaterial> findByBrandUid(UUID brandUid);
    
    /** Проверка существования материала по коду */
    boolean existsByCodeMaterial(Integer codeMaterial);
    
    /** Получение максимального кода номенклатуры */
    @Query("SELECT COALESCE(MAX(m.codeMaterial), 0) FROM SprMaterial m")
    Integer findMaxCodeMaterial();
}