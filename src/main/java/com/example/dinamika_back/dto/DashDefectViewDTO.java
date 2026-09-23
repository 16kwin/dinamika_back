package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Вид графика «Уровень брака»: столбики и средний уровень брака по предприятию
 * (сумма брака / сумма выпуска × 100) — по нему рисуется горизонтальная линия и красятся столбики.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashDefectViewDTO {
    private Integer released;
    private Integer defect;
    private BigDecimal averagePercent;
    private List<DashDefectSubjectDTO> subjects;
}
