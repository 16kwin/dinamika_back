package com.example.dinamika_back.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocRefillDto {
    private UUID uid;
    private Integer code;
    private LocalDateTime docDatetime;

    // Станция
    private String stationUid;
    private String stationName;
    private Integer stationCode;
    private Boolean isTmc;
    private Boolean isSgd;
    private Boolean isOk;
    private String accountingLabel;

    // Шаблон
    private UUID templateUid;
    private String templateName;
    private Long templateNumber;
    private String templateCategoryName;

    // Конфигурация / модель
    private UUID configurationUid;
    private String configurationName;
    private UUID modelUid;
    private String modelName;
    private String cellsStructureSnapshot;

    // Автор
    private Integer createdByUserId;
    private String createdByName;

    // Агрегаты
    private Integer totalCells;
    private Integer cellsToLoad;
    private Integer totalQuantity;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<RefillCellDto> cells;
}