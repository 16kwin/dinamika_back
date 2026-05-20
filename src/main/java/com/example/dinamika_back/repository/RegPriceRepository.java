package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Репозиторий для работы с регистром цен на материалы */
@Repository
public interface RegPriceRepository extends JpaRepository<RegPrice, UUID> {
    
    /** Найти все цены для конкретного материала */
    List<RegPrice> findByMaterialUid(UUID materialUid);
}