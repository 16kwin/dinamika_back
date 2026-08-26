package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegAttributes;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RegAttributesRepository extends JpaRepository<RegAttributes, UUID> {
    List<RegAttributes> findByMaterialUid(UUID materialUid);
    void deleteByMaterialUid(UUID materialUid);
    
    /** Найти все характеристики по виду характеристики */
    List<RegAttributes> findByAttributeTypeUid(UUID attributeTypeUid);
    
    /** Найти все характеристики по единице измерения */
    List<RegAttributes> findByMeasureUid(UUID measureUid);
}