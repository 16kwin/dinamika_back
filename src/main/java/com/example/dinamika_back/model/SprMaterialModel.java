package com.example.dinamika_back.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 3D-модель номенклатуры (вкладка «Характеристики» → «Изображение» → «3D»): исходный файл из САПР
 * и необязательная копия GLB, которую браузер открывает быстрее исходника.
 * Пути — относительно каталога nomenclature.models-dir.
 */
@Entity
@Table(name = "spr_material_models")
@Getter
@Setter
@NoArgsConstructor
public class SprMaterialModel {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @Column(name = "material_uid", nullable = false)
    private UUID materialUid;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "format", nullable = false, length = 16)
    private String format;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "view_path")
    private String viewPath;

    @Column(name = "view_size")
    private Long viewSize;

    @Column(name = "triangles")
    private Long triangles;

    @Column(name = "author")
    private String author;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
