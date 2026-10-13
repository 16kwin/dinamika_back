package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reg_refill_cells",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_refill_cell_address",
                columnNames = {"doc_refill_uid", "drum_number", "column_number", "number_cell"}
        ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegRefillCell {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_refill_uid", nullable = false)
    private DocRefill docRefill;

    @Column(name = "drum_number")
    private Integer drumNumber;

    @Column(name = "column_number")
    private Integer columnNumber;

    @Column(name = "number_cell")
    private Integer numberCell;

    // ==================== НАЗНАЧЕНИЕ (uid-ссылка) ====================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cell_assignment_uid")
    private SprCellAssignment cellAssignment;

    // ==================== НОМЕНКЛАТУРА (uid-ссылка) ====================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_uid")
    private SprMaterial material;

    // ==================== КОЛИЧЕСТВА ====================

    @Column(name = "template_quantity")
    private Integer templateQuantity;

    @Column(name = "fact_quantity")
    private Integer factQuantity;

    @Column(name = "refill_quantity")
    private Integer refillQuantity;

    // ==================== ПОРОГИ КОНТРОЛЯ ОСТАТКОВ ====================

    @Column(name = "min_stock_snapshot")
    private Integer minStockSnapshot;

    @Column(name = "critical_stock_snapshot")
    private Integer criticalStockSnapshot;

    @Column(name = "stock_control_exists", nullable = false)
    private Boolean stockControlExists;

    // ==================== ФЛАГИ ====================

    @Column(name = "return_to_this_cell", nullable = false)
    private Boolean returnToThisCell;

    @Column(name = "is_individual", nullable = false)
    private Boolean isIndividual;

    // ==================== СЛУЖЕБНЫЕ ====================

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (uid == null) uid = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (returnToThisCell == null) returnToThisCell = false;
        if (isIndividual == null) isIndividual = false;
        if (stockControlExists == null) stockControlExists = false;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}