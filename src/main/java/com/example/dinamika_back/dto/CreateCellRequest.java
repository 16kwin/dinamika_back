// CreateCellRequest.java — добавить поля
package com.example.dinamika_back.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateCellRequest {
    private UUID docPatternUid;
    private UUID materialUid;
    private Integer quantity;
    private String purposeMaterial;
    private String purposeSgd;
    private Integer maxQuantity;
    private UUID typeMainUid;
    private Integer numberCell;
    private Integer columnNumber;
    private Integer drumNumber;
}