package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprManufacturerDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SprManufacturerDocumentRepository extends JpaRepository<SprManufacturerDocument, UUID> {
    List<SprManufacturerDocument> findByManufacturerUidOrderByCreatedAtDesc(UUID manufacturerUid);
    void deleteByManufacturerUid(UUID manufacturerUid);
}