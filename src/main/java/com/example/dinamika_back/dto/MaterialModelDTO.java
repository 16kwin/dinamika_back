package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** 3D-модель номенклатуры для списка в окне «3D-модель». Пути на диске наружу не отдаются. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialModelDTO {
    private UUID uid;
    private UUID materialUid;
    private String originalName;
    /** glb, gltf, step, iges, vrml, stl, obj, 3mf, ply, x3d */
    private String format;
    private Long fileSize;
    /** Есть копия GLB для просмотра; без неё просмотр открывает исходник */
    private boolean hasView;
    private Long viewSize;
    private Long triangles;
    private String author;
    private LocalDateTime createdAt;
}
