package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Связь между поставщиками графа закупок (dash_graph_supplier_link):
 * kind='affiliated' — аффилированность, reason — основание («Общий учредитель» и т.п.).
 */
@Entity
@Table(name = "dash_graph_supplier_link")
@IdClass(DashGraphSupplierLinkId.class)
@Getter
@Setter
@NoArgsConstructor
public class DashGraphSupplierLink {

    @Id
    @Column(name = "supplier_a", nullable = false, length = 64)
    private String supplierA;

    @Id
    @Column(name = "supplier_b", nullable = false, length = 64)
    private String supplierB;

    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "reason")
    private String reason;
}
