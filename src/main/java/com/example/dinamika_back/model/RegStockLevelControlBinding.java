package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reg_stock_level_control_bindings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegStockLevelControlBinding {

    @Id
    @Column(name = "uid", columnDefinition = "uuid")
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_uid", referencedColumnName = "uid", nullable = false)
    private DocStockLevelControl doc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_uid", referencedColumnName = "uid")
    private SprMaterial material;

    @Column(name = "binding_date")
    private LocalDate bindingDate;

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