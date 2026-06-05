package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegSuppliers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Репозиторий для работы с регистром привязки поставщиков к материалам */
@Repository
public interface RegSuppliersRepository extends JpaRepository<RegSuppliers, UUID> {
    
    /** Найти всех поставщиков материала */
    List<RegSuppliers> findByMaterialUid(UUID materialUid);
    
    /** Найти все материалы поставщика */
    List<RegSuppliers> findBySupplierUid(UUID supplierUid);
    
    /** Удалить все привязки поставщиков к материалу */
    @Modifying
    @Transactional
    @Query("DELETE FROM RegSuppliers r WHERE r.material.uid = :materialUid")
    void deleteByMaterialUid(@Param("materialUid") UUID materialUid);
}