package com.example.dinamika_back.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransferFlatDto {
    private UUID uid;
    private Integer code;
    private LocalDateTime docDatetime;
    private String stockFromUid;
    private String stockFromName;
    private String stockToUid;
    private String stockToName;
    private String baseType;      // MANUAL / REFILL / MOVE_ORDER
    private UUID docBaseUid;
    private String priority;
    private LocalDateTime dueDate;
    private UUID operatorUid;
    private String operatorName;
    private String status;
    private Boolean agreed;
    private Boolean cancelled;
}