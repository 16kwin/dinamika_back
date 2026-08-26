package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprMeasure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SprMeasureRepository extends JpaRepository<SprMeasure, UUID> {
    List<SprMeasure> findByGroupUid(UUID groupUid);
}