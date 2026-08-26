package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprManufacturerImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SprManufacturerImageRepository extends JpaRepository<SprManufacturerImage, UUID> {
    List<SprManufacturerImage> findByManufacturerUidOrderBySortOrderAsc(UUID manufacturerUid);
    void deleteByManufacturerUid(UUID manufacturerUid);
}