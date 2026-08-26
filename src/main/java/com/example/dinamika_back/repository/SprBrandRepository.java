package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SprBrandRepository extends JpaRepository<SprBrand, UUID> {
    List<SprBrand> findByManufacturerUid(UUID manufacturerUid);
}