// CellRequest.java — без ClearBatchRequest
package com.example.dinamika_back.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CellRequest {
    private UUID materialUid;
    private Integer quantity;
    private UUID typeMainUid;
    private String purposeMaterial;
    private String purposeSgd;
    private Integer maxQuantity;
    private String dimensions;
}