// SprMaterialCode.java
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "spr_material_codes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SprMaterialCode {

    @Id
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_uid", nullable = false)
    private SprMaterial material;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "code_type")
    private String codeType;

    @Column(name = "code_value")
    private String codeValue;

    @Column(name = "code_kind")
    private String codeKind;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}