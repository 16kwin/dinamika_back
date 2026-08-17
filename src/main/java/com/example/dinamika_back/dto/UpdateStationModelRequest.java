// UpdateStationModelRequest.java
package com.example.dinamika_back.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UpdateStationModelRequest {
    private String name;
    private String article;
    private String revision;
    private UUID typeId;
    private UUID manufacturerId;
    private String purpose;
    
    // Параметры для генерации сетки ячеек
    private Integer columns;
    private Integer cellsPerColumn;
    private Integer drums;
    private Integer columnsPerDrum;
    private Integer rowsPerColumn;
    
    // Готовая структура ячеек (JSON)
    private String cellsStructure;
}