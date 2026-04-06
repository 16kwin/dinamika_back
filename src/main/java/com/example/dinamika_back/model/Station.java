package com.example.dinamika_back.model;

import com.example.dinamika_back.listener.StationEntityListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "stations")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(StationEntityListener.class)
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", unique = true, nullable = false, length = 50)
    private String uid;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "workshop", length = 100)
    private String workshop;

    @Column(name = "section", length = 100)
    private String section;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StationStatus status;

    @Column(name = "total_cells", nullable = false)
    private Integer totalCells;

    @Column(name = "filled_cells", nullable = false)
    private Integer filledCells;

    @Column(name = "template_nomenclature_count", nullable = false)
    private Integer templateNomenclatureCount;

    @Column(name = "remaining_nomenclature_count", nullable = false)
    private Integer remainingNomenclatureCount;

    @Column(name = "max_ready_parts", nullable = false)
    private Integer maxReadyParts;

    @Column(name = "ready_parts_count", nullable = false)
    private Integer readyPartsCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "station_type", nullable = false)
    private StationType stationType;

    @Column(name = "parent_uid", length = 50)
    private String parentUid;

    @Column(name = "has_error", nullable = false)
    private Boolean hasError;

    @Column(name = "is_tmc", nullable = false)
    private Boolean isTmc;

    @Column(name = "is_sgd", nullable = false)
    private Boolean isSgd;

    @Column(name = "is_ok", nullable = false)
    private Boolean isOk;

    @Column(name = "created_at", updatable = false)
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
}