package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Исполнение бюджета: план / факт за день, руб. (dash_budget_daily).
 */
@Entity
@Table(name = "dash_budget_daily")
@Getter
@Setter
@NoArgsConstructor
public class DashBudgetDaily {

    @Id
    @Column(name = "cost_date", nullable = false)
    private LocalDate costDate;

    @Column(name = "plan_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal planAmount;

    @Column(name = "fact_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal factAmount;
}
