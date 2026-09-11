package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Радар «Распределение затрат»: выбранные виды (оси) и их доли.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashDistributionDTO {
    /** Ключи выбранных видов — порядок осей по часовой стрелке */
    private List<String> selected;
    /** Значения в порядке selected */
    private List<DashDistributionItemDTO> items;
}
