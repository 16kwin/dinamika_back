package com.example.dinamika_back.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockLevelControlRegDto {
    private UUID uid;
    private String stationUid;
    private UUID materialUid;
    private Integer minStock;
    private Integer criticalStock;
}