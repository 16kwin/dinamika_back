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

    public StationDTO(Integer uid, String stationName, Integer modelNumber, 
                      Integer serialNumber, Integer currentCapacity) {
        this.uid = uid;
        this.stationName = stationName;
        this.modelNumber = modelNumber;
        this.serialNumber = serialNumber;
        this.currentCapacity = currentCapacity;
    }
}