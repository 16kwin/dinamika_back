package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Показатели затрат: закупки / выдача за день, руб. (dash_indicator_daily).
 */
@Entity
@Table(name = "dash_indicator_daily")
@Getter
@Setter
@NoArgsConstructor
public class DashIndicatorDaily {

    @Id
    @Column(name = "cost_date", nullable = false)
    private LocalDate costDate;

    @Column(name = "purchases_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal purchasesAmount;

    @Column(name = "issue_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal issueAmount;
}
