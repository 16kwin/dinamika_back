package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.GppRowDependency;
import com.example.dinamika_back.model.GppRowDependencyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GppRowDependencyRepository extends JpaRepository<GppRowDependency, GppRowDependencyId> {

    /** Зависимости строк документа в порядке предшественников */
    @Query("SELECT d FROM GppRowDependency d "
            + "WHERE d.rowUid IN (SELECT r.uid FROM GppRow r WHERE r.docUid = :docUid) "
            + "ORDER BY d.rowUid, d.position")
    List<GppRowDependency> findByDoc(@Param("docUid") UUID docUid);
}
