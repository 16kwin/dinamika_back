package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Настройки карточек панели «Экономический блок»: выбранные виды номенклатуры
 * (ответ GET и тело PATCH /api/dashboard/economic/settings).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSettingsDTO {
    /** Столбчатая диаграмма: 1..9 ключей, порядок важен */
    private List<String> barTypes;
    /** Радар: 5..9 ключей, порядок осей по часовой стрелке */
    private List<String> radarTypes;
}
