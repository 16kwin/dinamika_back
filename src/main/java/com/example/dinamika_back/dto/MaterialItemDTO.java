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
    
    // Поля для штрихкода и SKU
    private String barcode;
    private String sku;
    
    // Дополнительные поля для фильтрации и отображения
    private Integer rating;
    private String description;
    private Boolean usage;
    private Boolean wasteMaterial;
    private Boolean recycleMaterial;
    private String manufacturerName;
    private String countryName;
    private String brandName;
    private String modelName;
    private Double lastPrice;
}