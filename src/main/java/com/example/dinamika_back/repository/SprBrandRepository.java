package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SprBrandRepository extends JpaRepository<SprBrand, UUID> {
    List<SprBrand> findByManufacturerUid(UUID manufacturerUid);
}