package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprSupplierBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SprSupplierBrandRepository extends JpaRepository<SprSupplierBrand, UUID> {
    List<SprSupplierBrand> findBySupplierUid(UUID supplierUid);
}