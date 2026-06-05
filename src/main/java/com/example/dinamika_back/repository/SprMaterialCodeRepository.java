// SprMaterialCodeRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.SprMaterialCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

public interface SprMaterialCodeRepository extends JpaRepository<SprMaterialCode, UUID> {

    List<SprMaterialCode> findByMaterialUidOrderByCreatedAtDesc(UUID materialUid);

    List<SprMaterialCode> findByMaterialUidAndCodeKindOrderByCreatedAtDesc(UUID materialUid, String codeKind);

    @Modifying
    @Transactional
    @Query("DELETE FROM SprMaterialCode c WHERE c.material.uid = :materialUid")
    void deleteByMaterialUid(@Param("materialUid") UUID materialUid);
}