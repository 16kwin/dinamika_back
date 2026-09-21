package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * График «Расход объема производственной номенклатуры»: план/факт расхода ТМЦ за день (dash_production_daily).
 */
@Entity
@Table(name = "dash_production_daily")
@Getter
@Setter
@NoArgsConstructor
public class DashProductionDaily {

    @Id
    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "plan_qty", nullable = false, precision = 16, scale = 4)
    private BigDecimal planQty;

    @Column(name = "fact_qty", nullable = false, precision = 16, scale = 4)
    private BigDecimal factQty;
}
