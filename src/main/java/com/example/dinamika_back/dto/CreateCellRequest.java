// CreateCellRequest.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateCellRequest {
    private UUID docPatternUid;
    private Integer numberCell;
    private Integer columnNumber;
    private Integer drumNumber;
    private UUID cellAssignmentUid;
    private UUID materialUid;
    private Integer quantity;
    private Boolean returnToThisCell;
    private Boolean isIndividual;
}