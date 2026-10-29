package com.example.dinamika_back.dto;

import lombok.*;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransferLineDto {
    private UUID uid;
    private UUID docUid;
    private UUID materialUid;
    private String materialName;
    private String materialArticle;
    private Integer materialCode;
    private Integer quantity;
    private Integer acceptedQuantity;
    private UUID cellUid;
    private String cellName;
    private Integer available;
    private String batchInfo;
}