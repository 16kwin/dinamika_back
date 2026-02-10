package com.example.dinamika_back.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StationPositionDTO {
    private Integer stationId;
    private Integer locationId;
    private Integer coordX;
    private Integer coordY;
}