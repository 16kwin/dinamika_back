package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * График «Затраты на приобретение»: значение расходов (план / факт) в точке дня, руб. (dash_cost_daily).
 */
@Entity
@Table(name = "dash_cost_daily")
@Getter
@Setter
@NoArgsConstructor
public class DashCostDaily {

    @Id
    @Column(name = "cost_date", nullable = false)
    private LocalDate costDate;

    @Column(name = "plan_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal planAmount;

    @Column(name = "fact_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal factAmount;
}
