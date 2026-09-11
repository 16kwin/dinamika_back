package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Исполнение бюджета за период: план, факт и процент исполнения.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashBudgetDTO {
    private BigDecimal plan;
    private BigDecimal fact;
    /** fact / plan * 100, 2 знака; при plan = 0 → 0 */
    private BigDecimal percent;

    /** Конструктор для JPQL-проекции (суммы без процента) */
    public DashBudgetDTO(BigDecimal plan, BigDecimal fact) {
        this.plan = plan;
        this.fact = fact;
    }
}
