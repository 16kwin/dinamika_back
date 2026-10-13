package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "doc_refill")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocRefill {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @Column(name = "code")
    private Integer code;

    @Column(name = "doc_datetime", nullable = false)
    private LocalDateTime docDatetime;

    @Column(name = "station_uid", length = 50)
    private String stationUid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_uid", referencedColumnName = "uid", insertable = false, updatable = false)
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_uid")
    private DocPattern template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(name = "total_cells", nullable = false)
    private Integer totalCells;

    @Column(name = "cells_to_load", nullable = false)
    private Integer cellsToLoad;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    // ==================== КОНФИГУРАЦИЯ / МОДЕЛЬ (uid-ссылки) ====================

    @Column(name = "configuration_uid")
    private UUID configurationUid;

    @Column(name = "model_uid")
    private UUID modelUid;

    // ==================== СЛУЖЕБНЫЕ ====================

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "docRefill", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RegRefillCell> cells = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (uid == null) uid = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (docDatetime == null) docDatetime = now;
        if (status == null) status = "NEW";
        if (totalCells == null) totalCells = 0;
        if (cellsToLoad == null) cellsToLoad = 0;
        if (totalQuantity == null) totalQuantity = 0;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}