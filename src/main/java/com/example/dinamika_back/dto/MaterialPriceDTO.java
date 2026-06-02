package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MaterialPriceDTO {
    private UUID uid;
    private Double price;
    private LocalDateTime priceDate;
    private String supplierName;
    private Double previousPrice;
    private Double priceChange;
}