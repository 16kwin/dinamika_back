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

    /** Наименование шаблона */
    @Column(name = "name_pattern", nullable = false)
    private String namePattern;

    /** Номер шаблона (автоинкремент) */
    @Column(name = "number", unique = true)
    private Long number;

    /** Категория шаблона */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private TemplateCategory category;

    /** Конфигурация шаблона (пока пустая) */
    @Column(name = "configuration", columnDefinition = "TEXT DEFAULT ''")
    private String configuration;

    /** Общее количество ячеек */
    @Column(name = "total_cells", nullable = false)
    private Integer totalCells = 0;

    /** Количество заполненных ячеек */
    @Column(name = "filled_cells", nullable = false)
    private Integer filledCells = 0;

    /** Количество свободных ячеек */
    @Column(name = "free_cells", nullable = false)
    private Integer freeCells = 0;

    @Column(name = "created_at", updatable = false)
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
        if (configuration == null) configuration = "";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}