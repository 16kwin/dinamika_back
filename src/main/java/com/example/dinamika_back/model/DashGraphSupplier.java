package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Поставщик графа закупок (dash_graph_supplier); anchor — якорный (основной) поставщик.
 */
@Entity
@Table(name = "dash_graph_supplier")
@Getter
@Setter
@NoArgsConstructor
public class DashGraphSupplier {

    @Id
    @Column(name = "supplier_key", nullable = false, length = 64)
    private String supplierKey;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "anchor", nullable = false)
    private Boolean anchor;

    @Column(name = "inn", length = 12)
    private String inn;

    @Column(name = "city", length = 128)
    private String city;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
