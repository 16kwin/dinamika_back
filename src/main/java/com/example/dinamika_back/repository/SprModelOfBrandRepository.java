package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprModelOfBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SprModelOfBrandRepository extends JpaRepository<SprModelOfBrand, UUID> {
    List<SprModelOfBrand> findByBrandUid(UUID brandUid);
    List<SprModelOfBrand> findByManufacturerUid(UUID manufacturerUid);
}