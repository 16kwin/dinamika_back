// DocPattern.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "doc_pattern")
@Getter
@Setter
@NoArgsConstructor
public class DocPattern {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @Column(name = "name_pattern")
    private String namePattern;

    @Column(name = "number")
    private Long number;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private TemplateCategory category;

    @Column(name = "configuration", columnDefinition = "TEXT")
    private String configuration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "configuration_uid", referencedColumnName = "uid")
    private StationConfiguration stationConfiguration;

    @Column(name = "total_cells")
    private Integer totalCells;

    @Column(name = "filled_cells")
    private Integer filledCells;

    @Column(name = "free_cells")
    private Integer freeCells;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (totalCells == null) totalCells = 0;
        if (filledCells == null) filledCells = 0;
        if (freeCells == null) freeCells = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}