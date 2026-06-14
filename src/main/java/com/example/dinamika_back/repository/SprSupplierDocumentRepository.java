package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprSupplierDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface SprSupplierDocumentRepository extends JpaRepository<SprSupplierDocument, UUID> {

    List<SprSupplierDocument> findBySupplierUidOrderByCreatedAtDesc(UUID supplierUid);

    @Modifying
    @Transactional
    @Query("DELETE FROM SprSupplierDocument d WHERE d.supplier.uid = :supplierUid")
    void deleteBySupplierUid(UUID supplierUid);
}