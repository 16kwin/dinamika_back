package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reg_station_cells",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_station_cell_address",
                columnNames = {"station_uid", "drum_number", "column_number", "number_cell"}
        ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegStationCell {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_uid", referencedColumnName = "uid", nullable = false)
    private Station station;

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
    @JoinColumn(name = "material_uid")
    private SprMaterial material;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "return_to_this_cell", nullable = false)
    private Boolean returnToThisCell;

    @Column(name = "is_individual", nullable = false)
    private Boolean isIndividual;

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
        if (quantity == null) quantity = 0;
        if (returnToThisCell == null) returnToThisCell = false;
        if (isIndividual == null) isIndividual = false;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}