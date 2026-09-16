package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reg_stock_level_control",
        uniqueConstraints = @UniqueConstraint(columnNames = {"station_uid", "material_uid"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegStockLevelControl {

    @Id
    @Column(name = "uid", columnDefinition = "uuid")
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_uid", referencedColumnName = "uid", nullable = false)
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_uid", referencedColumnName = "uid", nullable = false)
    private SprMaterial material;

    @Column(name = "min_stock")
    private Integer minStock;

    @Column(name = "critical_stock")
    private Integer criticalStock;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (uid == null) uid = UUID.randomUUID();
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}