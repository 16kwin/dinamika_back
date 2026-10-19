// RegMoveOrderLine.java
package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reg_move_order_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegMoveOrderLine {

    @Id
    @Column(name = "uid", nullable = false)
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_uid", nullable = false)
    private DocMoveOrder doc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_uid")
    private SprMaterial material;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "stock_from_uid")
    private String stockFromUid;

    @Column(name = "priority", nullable = false)
    private String priority;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "shipping_at")
    private LocalDateTime shippingAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "loading_at")
    private LocalDateTime loadingAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "cancelled", nullable = false)
    private Boolean cancelled;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_uid")
    private DocTransfer transfer;

    // === Снимки на момент акцепта (V17) ===

    @Column(name = "material_name_snapshot", columnDefinition = "TEXT")
    private String materialNameSnapshot;

    @Column(name = "material_article_snapshot", columnDefinition = "TEXT")
    private String materialArticleSnapshot;

    @Column(name = "material_code_snapshot")
    private Integer materialCodeSnapshot;

    @Column(name = "available_snapshot")
    private Integer availableSnapshot;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @Column(name = "accepted_quantity")
private Integer acceptedQuantity;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}