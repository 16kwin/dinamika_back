package com.example.dinamika_back.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StationCellDto {
    private UUID uid;
    private String stationUid;

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
    private Integer materialCode;

    private Integer quantity;

    private Boolean returnToThisCell;
    private Boolean isIndividual;
}