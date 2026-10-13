package com.example.dinamika_back.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReserveRequest {
    private UUID docRefillUid;
    private String stockUid;      // 'MAIN' по умолчанию
    private String author;
    private List<StockReserveItem> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StockReserveItem {
        private UUID materialUid;
        private Integer quantity;
    }
}