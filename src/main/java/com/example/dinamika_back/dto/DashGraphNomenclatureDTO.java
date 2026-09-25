package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Номенклатура графа закупок с нормативной ценой. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashGraphNomenclatureDTO {
    private String key;
    private String name;
    private String groupKey;
    private String unit;
    /** Нормативная цена, руб. — база для лимита превышения */
    private BigDecimal refPrice;
    /** Избранная позиция */
    private Boolean favorite;
}
