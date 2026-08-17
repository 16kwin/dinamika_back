// StationModelDocumentRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StationModelDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StationModelDocumentRepository extends JpaRepository<StationModelDocument, UUID> {
    List<StationModelDocument> findByModelUidOrderByCreatedAtDesc(UUID modelUid);
    void deleteByModelUid(UUID modelUid);
}