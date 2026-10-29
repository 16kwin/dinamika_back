package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reg_transfer_lines")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegTransferLine {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_uid")
    private DocTransfer transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_uid")
    private SprMaterial material;

    private Integer quantity;

    @Column(name = "cell_uid")
    private UUID cellUid;

    @Column(name = "material_name_snapshot")
    private String materialNameSnapshot;

    @Column(name = "material_article_snapshot")
    private String materialArticleSnapshot;

    @Column(name = "material_code_snapshot")
    private Integer materialCodeSnapshot;

    @Column(name = "accepted_quantity")
    private Integer acceptedQuantity;

    @Column(name = "batch_info")
    private String batchInfo;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}