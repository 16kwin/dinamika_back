// StationDynamicDto.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StationDynamicDto {
    private String uid;
    private Double filledCellsPercent;
    private Double remainingNomenclaturePercent;
    private Double readyPartsPercent;
    
    // Исходные данные для вычислений
    private Integer totalCells;
    private Integer filledCells;
    private Integer templateNomenclatureCount;
    private Integer remainingNomenclatureCount;
    private Integer maxReadyParts;
    private Integer readyPartsCount;
}