// CellDto.java — добавить поля
package com.example.dinamika_back.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CellDto {
    private UUID uid;
    private Integer numberCell;
    private Integer columnNumber;
    private Integer drumNumber;
    private UUID materialUid;
    private String materialName;
    private String materialArticle;
    private Integer quantity;
    private UUID typeMainUid;
    private String typeMainName;
    private String purposeMaterial;
    private String purposeSgd;
    private Integer maxQuantity;
    private String dimensions;
}