package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegGroupMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Репозиторий для работы с иерархией групп материалов */
@Repository
public interface RegGroupMaterialRepository extends JpaRepository<RegGroupMaterial, UUID> {
    
    /** Найти все подгруппы родительской группы */
    List<RegGroupMaterial> findByParentGroup(UUID parentGroupId);
    
    /** Найти корневые группы (без родителя) */
    List<RegGroupMaterial> findByParentGroupIsNull();
}