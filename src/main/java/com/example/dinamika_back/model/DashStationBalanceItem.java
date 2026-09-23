package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Номенклатура станции с остатком и порогами (dash_station_balance_item).
 * Статус считается из остатка: quantity ≤ critical_level — КО, иначе quantity ≤ min_level — МО.
 */
@Entity
@Table(name = "dash_station_balance_item")
@Getter
@Setter
@NoArgsConstructor
public class DashStationBalanceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "station_key", nullable = false, length = 64)
    private String stationKey;

    @Column(name = "nom_name", nullable = false)
    private String nomName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "min_level", nullable = false)
    private Integer minLevel;

    @Column(name = "critical_level", nullable = false)
    private Integer criticalLevel;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
