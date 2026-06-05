// SprMaterialDocument.java
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "spr_material_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SprMaterialDocument {

    @Id
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_uid", nullable = false)
    private SprMaterial material;

    @Column(name = "document_name", nullable = false)
    private String documentName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}