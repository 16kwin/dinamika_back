// StockReservationDto.java
package com.example.dinamika_back.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservationDto {
    private UUID uid;
    private UUID docRefillUid;
    private Integer docRefillCode;

    private UUID materialUid;
    private String materialName;
    private String materialArticle;
    private Integer materialCode;

    private String stockUid;
    private Integer reservedQuantity;
    private Boolean blocked;

    // Автор брони (кто создал документ пополнения)
    private String author;
    private String authorFullName;

    // Станция и участок, куда едет номенклатура
    private String docStationUid;
    private String docStationName;
    private String docSectionName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}