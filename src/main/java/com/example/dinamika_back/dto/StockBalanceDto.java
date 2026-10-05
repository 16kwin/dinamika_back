package com.example.dinamika_back.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockBalanceDto {
    private UUID uid;
    private UUID materialUid;
    private String materialName;
    private String materialArticle;
    private Integer materialCode;
    private String stockUid;
    private Integer cleanQuantity;
    private Integer reservedQuantity;
    private Integer periodTotal;

    private Integer minStock;
    private Integer criticalStock;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}