package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Карточка-показатель панели оператора: значение, база расчёта доли и сама доля в процентах.
 * Доля задаёт, насколько заполнено кольцо карточки.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashOperatorMetricDTO {
    private String key;
    private String name;
    private Integer value;
    /** База доли: ёмкость станций, ТМЦ в станциях и т.п. */
    private Integer base;
    /** value / base × 100 */
    private BigDecimal percent;
}
