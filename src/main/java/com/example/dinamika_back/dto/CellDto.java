// CellDto.java — ПОЛНЫЙ ФАЙЛ
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
    private UUID cellAssignmentUid;
    private String cellAssignmentName;
    private UUID cellAssignmentTypeUid;
    private String cellAssignmentTypeName;
    private UUID materialUid;
    private String materialName;
    private String materialArticle;
    private Integer quantity;
    private Boolean returnToThisCell;
    private Boolean isIndividual;
}