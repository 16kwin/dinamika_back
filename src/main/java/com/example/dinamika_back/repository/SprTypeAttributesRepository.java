package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprTypeAttributes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SprTypeAttributesRepository extends JpaRepository<SprTypeAttributes, UUID> {
    List<SprTypeAttributes> findByGroupUid(UUID groupUid);
}