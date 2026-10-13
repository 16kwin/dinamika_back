package com.example.dinamika_back.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StationCellListResponse {
    private String stationUid;
    private List<StationCellDto> cells;
}