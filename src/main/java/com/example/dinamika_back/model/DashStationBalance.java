package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Станция (склад) графика критических и минимальных остатков (dash_station_balance).
 */
@Entity
@Table(name = "dash_station_balance")
@Getter
@Setter
@NoArgsConstructor
public class DashStationBalance {

    @Id
    @Column(name = "station_key", nullable = false, length = 64)
    private String stationKey;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
