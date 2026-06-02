package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprMaterialBlueprint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SprMaterialBlueprintRepository extends JpaRepository<SprMaterialBlueprint, UUID> {
    List<SprMaterialBlueprint> findByMaterialUid(UUID materialUid);
    void deleteByMaterialUid(UUID materialUid);
}