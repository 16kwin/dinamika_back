// CellRequest.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CellRequest {
    private UUID cellAssignmentUid;
    private UUID materialUid;
    private Integer quantity;
    private Boolean returnToThisCell;
    private Boolean isIndividual;
}