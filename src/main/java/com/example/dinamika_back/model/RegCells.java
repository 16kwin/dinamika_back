// RegCells.java — добавить поля
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reg_cells")
@Getter
@Setter
@NoArgsConstructor
public class RegCells {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_pattern_uid")
    private DocPattern docPattern;

    @Column(name = "number_cell")
    private Integer numberCell;

    @Column(name = "column_number")
    private Integer columnNumber;

    @Column(name = "drum_number")
    private Integer drumNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "name_material")
    private SprMaterial material;

    @Column(name = "quantity")
    private Integer quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_main")
    private SprTypeMaterial typeMain;

    @Column(name = "purpose_material")
    private String purposeMaterial;

    @Column(name = "purpose_sgd")
    private String purposeSgd;

    @Column(name = "max_quantity")
    private Integer maxQuantity;

    @Column(name = "dimensions")
    private String dimensions;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void clear() {
        this.material = null;
        this.quantity = null;
        this.typeMain = null;
        this.purposeMaterial = null;
        this.purposeSgd = null;
    }
}