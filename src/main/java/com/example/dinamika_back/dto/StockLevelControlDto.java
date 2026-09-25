package com.example.dinamika_back.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockLevelControlDto {
    private UUID uid;
    private Integer code;
    private LocalDate docDate;
    private String stationUid;
    private String stationName;
    private Boolean isPosted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<StockLevelControlBindingDto> bindings;
}