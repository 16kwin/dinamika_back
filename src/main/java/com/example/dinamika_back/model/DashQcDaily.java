package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Карточка «Производство»: прохождение контроля качества за день (dash_qc_daily).
 */
@Entity
@Table(name = "dash_qc_daily")
@Getter
@Setter
@NoArgsConstructor
public class DashQcDaily {

    @Id
    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "passed_qty", nullable = false, precision = 14, scale = 4)
    private BigDecimal passedQty;

    @Column(name = "waiting_qty", nullable = false, precision = 14, scale = 4)
    private BigDecimal waitingQty;

    @Column(name = "failed_qty", nullable = false, precision = 14, scale = 4)
    private BigDecimal failedQty;
}
