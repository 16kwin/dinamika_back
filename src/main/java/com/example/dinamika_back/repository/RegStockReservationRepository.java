// RegStockReservationRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegStockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegStockReservationRepository extends JpaRepository<RegStockReservation, UUID> {

    List<RegStockReservation> findByDocRefillUid(UUID docRefillUid);

    List<RegStockReservation> findByDocRefillUidAndBlockedFalse(UUID docRefillUid);

    List<RegStockReservation> findByMaterialUidAndStockUid(UUID materialUid, String stockUid);

    Optional<RegStockReservation> findByDocRefillUidAndMaterialUidAndStockUidAndBlockedFalse(
            UUID docRefillUid, UUID materialUid, String stockUid);

    @Query("SELECT r FROM RegStockReservation r " +
           "JOIN FETCH r.material " +
           "LEFT JOIN FETCH r.docRefill d " +
           "LEFT JOIN FETCH d.createdBy " +
           "LEFT JOIN FETCH d.station s " +
           "LEFT JOIN FETCH s.section " +
           "WHERE r.docRefill.uid = :docRefillUid")
    List<RegStockReservation> findByDocRefillUidWithMaterial(@Param("docRefillUid") UUID docRefillUid);

    @Query("SELECT r FROM RegStockReservation r " +
           "JOIN FETCH r.material " +
           "LEFT JOIN FETCH r.docRefill d " +
           "LEFT JOIN FETCH d.createdBy " +
           "LEFT JOIN FETCH d.station s " +
           "LEFT JOIN FETCH s.section " +
           "WHERE r.material.uid = :materialUid " +
           "AND r.stockUid = :stockUid " +
           "AND r.blocked = false")
    List<RegStockReservation> findActiveByMaterialWithRelations(
            @Param("materialUid") UUID materialUid,
            @Param("stockUid") String stockUid);

    @Modifying
    @Query("DELETE FROM RegStockReservation r WHERE r.docRefill.uid = :docRefillUid")
    void deleteByDocRefillUid(@Param("docRefillUid") UUID docRefillUid);

    @Modifying
    @Query("DELETE FROM RegStockReservation r WHERE r.docRefill.uid = :docRefillUid AND r.blocked = false")
    void deleteActiveByDocRefillUid(@Param("docRefillUid") UUID docRefillUid);

    @Modifying
    @Query("UPDATE RegStockReservation r SET r.blocked = true, r.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE r.docRefill.uid = :docRefillUid")
    void blockByDocRefillUid(@Param("docRefillUid") UUID docRefillUid);
}