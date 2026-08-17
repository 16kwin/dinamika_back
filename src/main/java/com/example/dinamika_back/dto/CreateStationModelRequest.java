// CreateStationModelRequest.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class CreateStationModelRequest {
    private UUID uid;
    private String name;
    private String article;
    private String revision;
    private UUID typeId;
    private UUID manufacturerId;
    private String purpose;
    
    // Параметры для генерации сетки
    private Integer columns;
    private Integer cellsPerColumn;
    private Integer drums;
    private Integer columnsPerDrum;
    private Integer rowsPerColumn;
    
    // Готовая структура ячеек (JSON)
    private String cellsStructure;
}