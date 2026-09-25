package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Заказ на закупку для графа закупок (dash_graph_purchase): одна строка — один заказ
 * позиции номенклатуры у поставщика.
 */
@Entity
@Table(name = "dash_graph_purchase")
@Getter
@Setter
@NoArgsConstructor
public class DashGraphPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "order_no", nullable = false, length = 32)
    private String orderNo;

    @Column(name = "nom_key", nullable = false, length = 64)
    private String nomKey;

    @Column(name = "supplier_key", nullable = false, length = 64)
    private String supplierKey;

    @Column(name = "purchase_at", nullable = false)
    private LocalDateTime purchaseAt;

    @Column(name = "qty", nullable = false, precision = 14, scale = 2)
    private BigDecimal qty;

    @Column(name = "price", nullable = false, precision = 14, scale = 2)
    private BigDecimal price;
}
