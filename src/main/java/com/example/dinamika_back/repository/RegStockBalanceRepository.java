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

    Optional<RegStockBalance> findByMaterialUidAndStockUid(UUID materialUid, String stockUid);

    List<RegStockBalance> findByStockUid(String stockUid);

    @Query("SELECT b FROM RegStockBalance b JOIN FETCH b.material WHERE b.stockUid = :stockUid")
    List<RegStockBalance> findByStockUidWithMaterial(@Param("stockUid") String stockUid);

    boolean existsByMaterialUidAndStockUid(UUID materialUid, String stockUid);
}