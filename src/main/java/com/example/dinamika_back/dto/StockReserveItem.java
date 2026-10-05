package com.example.dinamika_back.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReserveItem {
    private UUID materialUid;
    private Integer quantity;
}