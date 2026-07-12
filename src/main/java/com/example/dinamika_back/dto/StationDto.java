// StationDto.java — обновлённый конструктор
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StationDto {
    private String uid;
    private Integer code;
    private String name;
    private String description;
    private LocalDate productionDate;
    private String serialNumber;
    
    // Модель
    private String modelId;
    private String modelName;
    private String stationType;
    private String stationTypeUid;
    private String article;
    private String revision;
    
    // Размещение
    private Long holdingId;
    private String holdingName;
    private Long enterpriseId;
    private String enterpriseName;
    private Long workshopId;
    private String workshopName;
    private Long sectionId;
    private String sectionName;
    
    // Статус
    private String status;
    
    // Конфигурация
    private String configurationUid;
    private String configurationName;
    
    // Доп. модули
    private String parentUid;
    private Boolean isAdditionalModule;
    private Boolean hasAdditionalModule;
    
    // Вид учёта
    private Boolean hasError;
    private Boolean isTmc;
    private Boolean isSgd;
    private Boolean isOk;
    
    // Сеть
    private String ipAddress;
    private Integer networkPort;
    
    // Шаблон
    private String activeTemplateUid;
    private String activeTemplateName;
}