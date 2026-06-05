// RegRatingRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

public interface RegRatingRepository extends JpaRepository<RegRating, UUID> {

    List<RegRating> findByMaterialUidOrderByCreatedAtDesc(UUID materialUid);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM RegRating r WHERE r.material.uid = :materialUid")
    Double getAverageRatingByMaterialUid(@Param("materialUid") UUID materialUid);

    @Query("SELECT COUNT(r) FROM RegRating r WHERE r.material.uid = :materialUid")
    Long countByMaterialUid(@Param("materialUid") UUID materialUid);

    @Modifying
    @Transactional
    @Query("DELETE FROM RegRating r WHERE r.material.uid = :materialUid")
    void deleteByMaterialUid(@Param("materialUid") UUID materialUid);
}