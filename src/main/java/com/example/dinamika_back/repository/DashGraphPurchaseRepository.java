package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DashGraphPurchase;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DashGraphPurchaseRepository extends JpaRepository<DashGraphPurchase, Long> {

    /** Заказы с моментом в [dateFrom, dateTo) по возрастанию времени */
    @Query("SELECT p FROM DashGraphPurchase p "
            + "WHERE p.purchaseAt >= :dateFrom AND p.purchaseAt < :dateTo "
            + "ORDER BY p.purchaseAt ASC, p.id ASC")
    List<DashGraphPurchase> findByPeriod(@Param("dateFrom") LocalDateTime dateFrom,
                                         @Param("dateTo") LocalDateTime dateTo);

    /**
     * Заказы с моментом в [dateFrom, dateTo), где цена выше нормативной цены номенклатуры × factor,
     * самые поздние первыми (размер ленты задаёт pageable)
     */
    @Query("SELECT p FROM DashGraphPurchase p, DashGraphNomenclature n "
            + "WHERE n.nomKey = p.nomKey "
            + "AND p.purchaseAt >= :dateFrom AND p.purchaseAt < :dateTo "
            + "AND p.price > n.refPrice * :factor "
            + "ORDER BY p.purchaseAt DESC, p.id DESC")
    List<DashGraphPurchase> findOverpriced(@Param("dateFrom") LocalDateTime dateFrom,
                                           @Param("dateTo") LocalDateTime dateTo,
                                           @Param("factor") BigDecimal factor,
                                           Pageable pageable);
}
