package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Номенклатура графа закупок (dash_graph_nomenclature). ref_price — нормативная цена:
 * закупка «свыше лимита», если средняя цена пары выше ref_price × (1 + лимит/100).
 */
@Entity
@Table(name = "dash_graph_nomenclature")
@Getter
@Setter
@NoArgsConstructor
public class DashGraphNomenclature {

    @Id
    @Column(name = "nom_key", nullable = false, length = 64)
    private String nomKey;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "group_key", nullable = false, length = 64)
    private String groupKey;

    @Column(name = "unit", nullable = false, length = 16)
    private String unit;

    @Column(name = "ref_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal refPrice;

    @Column(name = "favorite", nullable = false)
    private Boolean favorite;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
