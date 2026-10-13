package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    List<StockMovement> findByDocUid(UUID docUid);

    List<StockMovement> findByDocUidAndStatus(UUID docUid, String status);

    List<StockMovement> findByStockUidAndMaterialUidOrderByPeriodDesc(String stockUid, UUID materialUid);

    List<StockMovement> findByStockUidAndMaterialUid(String stockUid, UUID materialUid);

    List<StockMovement> findByDocBaseUid(UUID docBaseUid);

    List<StockMovement> findByStatusOrderByPeriodDesc(String status);

    @Modifying
    @Query("DELETE FROM StockMovement m WHERE m.docUid = :docUid AND m.status = 'DRAFT'")
    void deleteDraftByDocUid(@Param("docUid") UUID docUid);

    @Modifying
    @Query("DELETE FROM StockMovement m WHERE m.docUid = :docUid")
    void deleteByDocUid(@Param("docUid") UUID docUid);

    @Modifying
    @Query("UPDATE StockMovement m SET m.status = :status, m.period = CURRENT_TIMESTAMP, " +
           "m.updatedAt = CURRENT_TIMESTAMP WHERE m.docUid = :docUid")
    void updateStatusByDocUid(@Param("docUid") UUID docUid, @Param("status") String status);

    @Query("SELECT COALESCE(SUM(m.quantity), 0) FROM StockMovement m " +
           "WHERE m.stockUid = :stockUid AND m.material.uid = :materialUid AND m.status = 'POSTED'")
    Integer sumPostedQuantity(@Param("stockUid") String stockUid,
                              @Param("materialUid") UUID materialUid);
}