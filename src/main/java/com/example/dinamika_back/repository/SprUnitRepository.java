package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SprUnitRepository extends JpaRepository<SprUnit, UUID> {
}