package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.GppRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GppRowRepository extends JpaRepository<GppRow, UUID> {

    List<GppRow> findByDocUid(UUID docUid);

    /** Все строки документа; дети и зависимости удаляются каскадом в базе */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM GppRow r WHERE r.docUid = :docUid")
    int deleteByDoc(@Param("docUid") UUID docUid);
}
