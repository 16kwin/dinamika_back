package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprMaterialModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SprMaterialModelRepository extends JpaRepository<SprMaterialModel, UUID> {

    List<SprMaterialModel> findByMaterialUidOrderByCreatedAtAsc(UUID materialUid);

    void deleteByMaterialUid(UUID materialUid);
}
