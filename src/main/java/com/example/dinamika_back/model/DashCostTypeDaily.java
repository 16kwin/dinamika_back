package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Затраты по виду номенклатуры за день, руб. (dash_cost_type_daily).
 */
@Entity
@Table(name = "dash_cost_type_daily")
@Getter
@Setter
@NoArgsConstructor
public class DashCostTypeDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cost_date", nullable = false)
    private LocalDate costDate;

    /** Ключ вида номенклатуры (dash_nomenclature_type.type_key) */
    @Column(name = "type_key", nullable = false, length = 64)
    private String typeKey;

    @Column(name = "amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal amount;
}
