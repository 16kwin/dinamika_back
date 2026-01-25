package com.example.dinamika_back.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateStationDTO {
    private String stationName;
    private Integer modelNumber;
    private Integer serialNumber;
    private Integer currentCapacity;
    private String ipAddress;
    
    // Новые поля
    private Boolean isEnabled;
    private Integer capacity;
    private Integer fullness;
    private Boolean hasErrors;
    private Integer issued;
    private Integer issuedOverNorm;
    private Integer finishedParts;
    
    // ID локаций (могут быть null)
    private Integer level1FactoryId;
    private Integer level2ObjectId;
    private Integer level3ZoneId;
}