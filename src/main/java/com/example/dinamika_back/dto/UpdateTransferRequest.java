package com.example.dinamika_back.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateTransferRequest {
    private LocalDateTime docDatetime;
    private String stockFromUid;
    private String stockToUid;
    private String priority;
    private LocalDateTime dueDate;
    private UUID operatorUid;
    private List<LineUpdate> lines;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LineUpdate {
        private UUID uid;
        private UUID materialUid;
        private Integer quantity;
        private UUID cellUid;
        private String batchInfo;
    }
}