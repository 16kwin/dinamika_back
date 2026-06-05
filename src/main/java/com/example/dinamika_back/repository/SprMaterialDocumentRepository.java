// SprMaterialDocumentRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprMaterialDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

public interface SprMaterialDocumentRepository extends JpaRepository<SprMaterialDocument, UUID> {

    List<SprMaterialDocument> findByMaterialUidOrderByCreatedAtDesc(UUID materialUid);

    @Modifying
    @Transactional
    @Query("DELETE FROM SprMaterialDocument d WHERE d.material.uid = :materialUid")
    void deleteByMaterialUid(@Param("materialUid") UUID materialUid);
}