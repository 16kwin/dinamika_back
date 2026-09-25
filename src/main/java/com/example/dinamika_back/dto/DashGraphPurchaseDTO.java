package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Закупки пары номенклатура × поставщик за период (ребро графа). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashGraphPurchaseDTO {
    private String nomKey;
    private String supplierKey;
    /** Число заказов пары в периоде */
    private Integer orders;
    /** Σ количества */
    private BigDecimal qty;
    /** Σ(количество × цена), руб. */
    private BigDecimal amount;
    /** Средняя цена = amount / qty, 2 знака */
    private BigDecimal avgPrice;
    /** Момент и номер последнего заказа пары в периоде */
    private LocalDateTime lastAt;
    private String lastOrderNo;
}
