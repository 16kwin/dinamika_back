package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "spr_manufacturer_images")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SprManufacturerImage {
    @Id
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer_uid")
    private SprManufacturer manufacturer;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}