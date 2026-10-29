package com.example.dinamika_back.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateTransferRequest {
    private UUID uid;
    private LocalDateTime docDatetime;
    private String stockFromUid;
    private String stockToUid;
    private String baseType;      // MANUAL / REFILL / MOVE_ORDER
    private UUID docBaseUid;
    private String priority;
    private LocalDateTime dueDate;
    private UUID operatorUid;
    private List<TransferLineRequest> lines;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TransferLineRequest {
        private UUID materialUid;
        private Integer quantity;
        private UUID cellUid;
        private String batchInfo;
    }
}