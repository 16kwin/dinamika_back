package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprSupplierImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface SprSupplierImageRepository extends JpaRepository<SprSupplierImage, UUID> {

    List<SprSupplierImage> findBySupplierUidOrderBySortOrderAsc(UUID supplierUid);

    @Modifying
    @Transactional
    @Query("DELETE FROM SprSupplierImage i WHERE i.supplier.uid = :supplierUid")
    void deleteBySupplierUid(UUID supplierUid);
}