package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "spr_manufacturer_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SprManufacturerDocument {
    @Id
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer_uid")
    private SprManufacturer manufacturer;

    @Column(name = "document_name")
    private String documentName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}