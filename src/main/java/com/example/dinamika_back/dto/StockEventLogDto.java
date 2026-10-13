package com.example.dinamika_back.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockEventLogDto {
    private UUID uid;
    private UUID balanceUid;
    private UUID reservationUid;
    private UUID materialUid;
    private String materialName;
    private UUID docRefillUid;
    private Integer docRefillCode;
    private String eventType;
    private String eventDescription;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String author;
    private String source;
    private LocalDateTime createdAt;
}