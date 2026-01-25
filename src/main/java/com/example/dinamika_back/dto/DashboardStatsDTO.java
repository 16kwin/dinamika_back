package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {
    private Long totalStations;           // 1) количество станций
    private Long totalFullness;           // 2) сумма заполненности (fullness)
    private Long totalCapacity;           // 3) сумма всех мест в станциях
    private Long totalIssued;             // 4) сумма выданных деталей
    private Long totalIssuedOverNorm;     // 5) сумма выданного сверхнормы
}