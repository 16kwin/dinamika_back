package com.example.dinamika_back.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StationDTO {
    private Integer uid; // Integer из сущности
    private String stationName;
    private Integer modelNumber;
    private Integer serialNumber;
    private Integer currentCapacity;
    
    // Новые поля
    private Boolean isEnabled;
    private Integer capacity;
    private Integer fullness;
    private Boolean hasErrors;
    private Integer issued;
    private Integer issuedOverNorm;
    private Integer finishedParts;

    public StationDTO(Integer uid, String stationName, Integer modelNumber, 
                      Integer serialNumber, Integer currentCapacity,
                      Boolean isEnabled, Integer capacity, Integer fullness,
                      Boolean hasErrors, Integer issued, Integer issuedOverNorm,
                      Integer finishedParts) {
        this.uid = uid;
        this.stationName = stationName;
        this.modelNumber = modelNumber;
        this.serialNumber = serialNumber;
        this.currentCapacity = currentCapacity;
        this.isEnabled = isEnabled;
        this.capacity = capacity;
        this.fullness = fullness;
        this.hasErrors = hasErrors;
        this.issued = issued;
        this.issuedOverNorm = issuedOverNorm;
        this.finishedParts = finishedParts;
    }
}