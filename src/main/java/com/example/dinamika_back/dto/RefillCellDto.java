package com.example.dinamika_back.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefillCellDto {
    private UUID uid;
    private Integer drumNumber;
    private Integer columnNumber;
    private Integer numberCell;

    // Назначение
    private UUID cellAssignmentUid;
    private String cellAssignmentName;
    private UUID cellAssignmentTypeUid;
    private String cellAssignmentTypeName;

    // Номенклатура
    private UUID materialUid;
    private String materialName;
    private String materialArticle;
    private Integer materialCode;
    private String materialGroupName;
    private String materialMeasureName;
    private Boolean materialUsage;
    private String materialTypeName;
    private String materialPurposeName;
    private String materialProductName;
    private String materialManufacturerName;
    private String materialCountryName;
    private String materialBrandName;
    private String materialModelName;

    // Количества
    private Integer templateQuantity;
    private Integer factQuantity;
    private Integer refillQuantity;

    // Остаток на основном складе (чистый, не забронированный)
    private Integer stockAvailable;

    // Пороги склада (мин/крит)
    private Integer stockMinStock;
    private Integer stockCriticalStock;

    // Пороги КУО (по станции)
    private Integer minStockSnapshot;
    private Integer criticalStockSnapshot;
    private Boolean stockControlExists;

    // Флаги
    private Boolean returnToThisCell;
    private Boolean isIndividual;
}