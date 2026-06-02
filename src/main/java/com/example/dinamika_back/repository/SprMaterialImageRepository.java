package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprMaterialImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SprMaterialImageRepository extends JpaRepository<SprMaterialImage, UUID> {
    List<SprMaterialImage> findByMaterialUidOrderBySortOrderAsc(UUID materialUid);
    void deleteByMaterialUid(UUID materialUid);
}