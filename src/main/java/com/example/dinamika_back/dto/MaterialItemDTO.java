package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialItemDTO {
    private UUID uid;
    private String name;
    private String article;
    private String unit;
    private Integer quantity;
    private Double price;
    private Integer code; // добавить код номенклатуры
}