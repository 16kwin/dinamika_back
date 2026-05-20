package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegSuppliers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Репозиторий для работы с регистром привязки поставщиков к материалам */
@Repository
public interface RegSuppliersRepository extends JpaRepository<RegSuppliers, UUID> {
    
    /** Найти всех поставщиков материала */
    List<RegSuppliers> findByMaterialUid(UUID materialUid);
    
    /** Найти все материалы поставщика */
    List<RegSuppliers> findBySupplierUid(UUID supplierUid);
}