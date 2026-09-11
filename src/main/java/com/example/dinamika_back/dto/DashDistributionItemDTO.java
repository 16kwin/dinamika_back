package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Ось радара: вид номенклатуры, сумма за период и процент от максимума среди выбранных.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashDistributionItemDTO {
    private String key;
    private String name;
    private BigDecimal amount;
    /** amount / (max * 1.1) * 100, 1 знак (максимум = 90.9); при max = 0 → 0 */
    private BigDecimal percent;
}
