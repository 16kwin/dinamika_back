package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Строка ленты «Закупки с завышенной ценой»: один заказ с ценой выше нормативной. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashAuditOverpricedDTO {
    private Long id;
    private String orderNo;
    private LocalDateTime at;
    private String nomKey;
    private String nomName;
    private String supplierKey;
    private String supplierName;
    /** Цена заказа, руб. */
    private BigDecimal price;
    /** Нормативная цена номенклатуры, руб. */
    private BigDecimal refPrice;
    /** Превышение (price / refPrice − 1) × 100, 1 знак */
    private BigDecimal overPercent;
    private BigDecimal qty;
    /** qty × price, руб. */
    private BigDecimal amount;
}
