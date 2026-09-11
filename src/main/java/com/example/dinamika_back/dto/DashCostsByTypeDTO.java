package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Столбчатая диаграмма «Затраты по видам номенклатуры»: выбранные виды и суммы за период.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashCostsByTypeDTO {
    /** Ключи выбранных видов (порядок столбцов) */
    private List<String> selected;
    /** Суммы в порядке selected */
    private List<DashTypeAmountDTO> items;
}
