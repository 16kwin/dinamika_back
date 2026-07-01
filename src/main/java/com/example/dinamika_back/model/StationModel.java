package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "station_models")
@Getter
@Setter
@NoArgsConstructor
public class StationModel {

    @Id
    @Column(name = "uid", updatable = false, nullable = false)
    private UUID uid;

    @Column(name = "code", nullable = false, unique = true)
    private Integer code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "article", length = 255)
    private String article;

    @Column(name = "revision", length = 255)
    private String revision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private StationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer_id")
    private StationManufacturer manufacturer;

    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "cells_structure", columnDefinition = "TEXT")
    private String cellsStructure;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (uid == null) uid = UUID.randomUUID();
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}