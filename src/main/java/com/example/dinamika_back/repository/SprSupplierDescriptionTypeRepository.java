package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprSupplierDescriptionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SprSupplierDescriptionTypeRepository extends JpaRepository<SprSupplierDescriptionType, UUID> {
}