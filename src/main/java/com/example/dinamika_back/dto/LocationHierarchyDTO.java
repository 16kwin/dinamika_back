package com.example.dinamika_back.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LocationHierarchyDTO {
    private Integer id; // Integer чтобы соответствовать сущности
    private String locationName;
    private Integer level;
    private List<LocationHierarchyDTO> childLocations;
    private List<StationDTO> stations;

    public LocationHierarchyDTO(Integer id, String locationName, Integer level) {
        this.id = id;
        this.locationName = locationName;
        this.level = level;
        this.childLocations = new java.util.ArrayList<>();
        this.stations = new java.util.ArrayList<>();
    }
}