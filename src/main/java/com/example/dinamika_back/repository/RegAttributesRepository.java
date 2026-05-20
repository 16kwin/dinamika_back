package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegAttributes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Репозиторий для работы с регистром атрибутов материалов */
@Repository
public interface RegAttributesRepository extends JpaRepository<RegAttributes, UUID> {
    
    /** Найти все атрибуты конкретного вида */
    List<RegAttributes> findByAttributeTypeUid(UUID attributeTypeUid);
}