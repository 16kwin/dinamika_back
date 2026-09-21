package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Карточка «Показатели качества»: годный выпуск и брак за день (dash_quality_daily).
 */
@Entity
@Table(name = "dash_quality_daily")
@Getter
@Setter
@NoArgsConstructor
public class DashQualityDaily {

    @Id
    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "released_qty", nullable = false, precision = 14, scale = 4)
    private BigDecimal releasedQty;

    @Column(name = "defect_qty", nullable = false, precision = 14, scale = 4)
    private BigDecimal defectQty;
}
