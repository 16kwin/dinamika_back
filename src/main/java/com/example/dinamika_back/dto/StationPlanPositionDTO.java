package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Позиция станции на плане: доли 0..1 от ширины (x) и высоты (y) плана */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StationPlanPositionDTO {
    private String stationUid;
    private Double x;
    private Double y;
}
