package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Ответ GET /api/dashboard/economic — все карточки панели «Экономический блок» за период.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardEconomicResponse {
    private LocalDate from;
    private LocalDate to;
    /** График «Затраты на приобретение» */
    private DashCostsDTO costs;
    /** Столбчатая диаграмма «Затраты по видам номенклатуры» */
    private DashCostsByTypeDTO costsByType;
    /** Показатели затрат: закупки / выдача */
    private DashIndicatorsDTO indicators;
    /** Исполнение бюджета */
    private DashBudgetDTO budget;
    /** Радар «Распределение затрат» */
    private DashDistributionDTO distribution;
    /** Все виды номенклатуры в порядке sort_order (для выбора в карточках) */
    private List<DashTypeDTO> availableTypes;
}
