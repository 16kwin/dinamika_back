package com.example.dinamika_back.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * План цеха для интерактивной карты: GET /api/station-plans/workshops/{workshopId}.
 * Если плана ещё нет — version 0, image null, positions пустой.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StationPlanDTO {
    private Long workshopId;
    private Long version;
    /** Картинка плана; null — карта рисует автоматическую схему цеха */
    private StationPlanImageDTO image;
    /** Позиции станций, которые сейчас числятся в этом цехе */
    private List<StationPlanPositionDTO> positions;
    /**
     * Только в ответе сохранения расстановки: uid станций из запроса, позиции которых не сохранены —
     * станцию удалили или перевели в другой цех, пока шла правка. Поля нет — пропусков не было.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> skippedStationUids;

    public StationPlanDTO(Long workshopId, Long version, StationPlanImageDTO image, List<StationPlanPositionDTO> positions) {
        this(workshopId, version, image, positions, null);
    }
}
