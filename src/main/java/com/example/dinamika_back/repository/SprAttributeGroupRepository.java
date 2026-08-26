package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprAttributeGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SprAttributeGroupRepository extends JpaRepository<SprAttributeGroup, UUID> {
}