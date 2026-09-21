package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Показатели панели оператора склада на дату: ТМЦ в станциях, выдача, детали на СГД
 * (dash_operator_metric_daily).
 */
@Entity
@Table(name = "dash_operator_metric_daily")
@Getter
@Setter
@NoArgsConstructor
public class DashOperatorMetricDaily {

    @Id
    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "tmc_in_stations", nullable = false)
    private Integer tmcInStations;

    @Column(name = "tmc_capacity", nullable = false)
    private Integer tmcCapacity;

    @Column(name = "issued_tmc", nullable = false)
    private Integer issuedTmc;

    @Column(name = "issued_over_norm", nullable = false)
    private Integer issuedOverNorm;

    @Column(name = "sgd_parts", nullable = false)
    private Integer sgdParts;

    @Column(name = "sgd_capacity", nullable = false)
    private Integer sgdCapacity;
}
