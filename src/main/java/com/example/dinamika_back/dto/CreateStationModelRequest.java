// CreateStationModelRequest.java
package com.example.dinamika_back.dto;

import lombok.Getter;
import lombok.Setter;

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
    
    // Параметры для генерации сетки ячеек
    private Integer columns;          // для постамата: количество колонок
    private Integer cellsPerColumn;   // для постамата: ячеек в колонке
    private Integer drums;            // для барабана: количество барабанов
    private Integer columnsPerDrum;   // для барабана: колонок в барабане
    private Integer rowsPerColumn;    // для барабана: строк в колонке
}