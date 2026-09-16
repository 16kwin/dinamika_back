// RegCells.java — ПОЛНЫЙ ФАЙЛ (новая структура)
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
    @JoinColumn(name = "cell_assignment_uid")
    private SprCellAssignment cellAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "name_material")
    private SprMaterial material;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "return_to_this_cell", nullable = false)
    private Boolean returnToThisCell = false;

    @Column(name = "is_individual", nullable = false)
    private Boolean isIndividual = false;

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
        this.cellAssignment = null;
        this.returnToThisCell = false;
        this.isIndividual = false;
    }
}