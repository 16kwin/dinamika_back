package com.example.dinamika_back.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransferDetailDto {
    private UUID uid;
    private Integer code;
    private LocalDateTime docDatetime;
    private String stockFromUid;
    private String stockFromName;
    private String stockToUid;
    private String stockToName;
    private String baseType;
    private UUID docBaseUid;
    private String docBaseLabel;   // «Заказ на перемещение №0001 от ...»
    private String priority;
    private LocalDateTime dueDate;
    private UUID operatorUid;
    private String operatorName;
    private String status;
    private Boolean agreed;
    private Boolean cancelled;
    private List<TransferLineDto> lines;
}