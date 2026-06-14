package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegSupplierRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface RegSupplierRatingRepository extends JpaRepository<RegSupplierRating, UUID> {

    List<RegSupplierRating> findBySupplierUidOrderByCreatedAtDesc(UUID supplierUid);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM RegSupplierRating r WHERE r.supplier.uid = :supplierUid")
    Double getAverageRatingBySupplierUid(UUID supplierUid);

    @Modifying
    @Transactional
    @Query("DELETE FROM RegSupplierRating r WHERE r.supplier.uid = :supplierUid")
    void deleteBySupplierUid(UUID supplierUid);
}