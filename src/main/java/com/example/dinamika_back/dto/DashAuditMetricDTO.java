package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Показатель аудитора: значение, база (всего операций) и доля в процентах. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashAuditMetricDTO {
    private Integer value;
    private Integer base;
    /** value / base × 100, 1 знак; при base = 0 — 0 */
    private BigDecimal percent;
}
