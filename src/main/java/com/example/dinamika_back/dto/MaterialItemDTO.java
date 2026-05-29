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
    private Integer code;
    private String unit;
    private Integer quantity;
    private Double price;
    
    // Новые поля для отображения в таблице справочника
    private String typeMainName;
    private String typePurposeName;
    private String typeProductName;
}