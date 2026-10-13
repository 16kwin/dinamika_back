package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegStockBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegStockBalanceRepository extends JpaRepository<RegStockBalance, UUID> {

    /** Последняя запись по паре (номенклатура + склад) — текущее состояние остатка. */
    Optional<RegStockBalance> findTopByMaterialUidAndStockUidOrderByCreatedAtDesc(UUID materialUid, String stockUid);

    /** Последняя запись по uid desc — если createdAt совпадает (быстрая вставка). */
    Optional<RegStockBalance> findTopByMaterialUidAndStockUidOrderByUidDesc(UUID materialUid, String stockUid);

    /** Все записи по номенклатуре на складе — история. */
    List<RegStockBalance> findByMaterialUidAndStockUidOrderByCreatedAtDesc(UUID materialUid, String stockUid);

    /** Все записи по складу. */
    List<RegStockBalance> findByStockUid(String stockUid);

    /** Все записи по складу с номенклатурой (join fetch). */
    @Query("SELECT b FROM RegStockBalance b JOIN FETCH b.material WHERE b.stockUid = :stockUid")
    List<RegStockBalance> findByStockUidWithMaterial(@Param("stockUid") String stockUid);

    boolean existsByMaterialUidAndStockUid(UUID materialUid, String stockUid);
}