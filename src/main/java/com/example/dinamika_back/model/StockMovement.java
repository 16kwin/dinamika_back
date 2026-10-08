package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_movement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    @GeneratedValue
    @Column(name = "uid", nullable = false, updatable = false)
    private UUID uid;

    @Column(name = "doc_uid")
    private UUID docUid;

    @Column(name = "doc_type", nullable = false, length = 50)
    private String docType;

    @Column(name = "doc_code")
    private Integer docCode;

    @Column(name = "doc_base_uid")
    private UUID docBaseUid;

    @Column(name = "doc_base_type", length = 50)
    private String docBaseType;

    @Column(name = "stock_uid", nullable = false, length = 50)
    private String stockUid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_uid", nullable = false)
    private SprMaterial material;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "period", nullable = false)
    private LocalDateTime period;

    @Column(name = "is_reusable")
    private Boolean isReusable;

    @Column(name = "is_resharpen")
    private Boolean isResharpen;

    @Column(name = "is_scrap")
    private Boolean isScrap;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (period == null) period = now;
        if (status == null) status = "DRAFT";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}