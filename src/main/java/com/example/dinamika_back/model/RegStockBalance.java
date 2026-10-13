package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reg_stock_balance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegStockBalance {

    @Id
    @GeneratedValue
    @Column(name = "uid", nullable = false, updatable = false)
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_uid", nullable = false)
    private SprMaterial material;

    @Column(name = "stock_uid", nullable = false, length = 50)
    private String stockUid;

    @Column(name = "clean_quantity", nullable = false)
    private Integer cleanQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity;

    @Column(name = "period_total")
    private Integer periodTotal;

    @Column(name = "min_stock")
    private Integer minStock;

    @Column(name = "critical_stock")
    private Integer criticalStock;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (cleanQuantity == null) cleanQuantity = 0;
        if (reservedQuantity == null) reservedQuantity = 0;
        if (stockUid == null) stockUid = "MAIN";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}