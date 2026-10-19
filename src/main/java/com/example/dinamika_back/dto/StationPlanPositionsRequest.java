package com.example.dinamika_back.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Тело PUT /api/station-plans/workshops/{workshopId}/positions — полная замена расстановки.
 * version — версия плана, с которой начиналась правка; если план успели изменить, ответ 409.
 */
@Data
@NoArgsConstructor
public class StationPlanPositionsRequest {
    private Long version;
    private List<StationPlanPositionDTO> positions;
}
