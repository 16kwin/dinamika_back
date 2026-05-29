package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprTypeProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Репозиторий для справочника "Виды номенклатуры" */
@Repository
public interface SprTypeProductRepository extends JpaRepository<SprTypeProduct, UUID> {
    
    /** Найти все виды номенклатуры, относящиеся к указанной группе номенклатуры */
    List<SprTypeProduct> findByTypePurposeUid(UUID typePurposeUid);
}