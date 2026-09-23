package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** Столбик графика «Уровень брака»: деталь или подразделение с раскрытием для pop-up. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashDefectSubjectDTO {
    private String key;
    private String name;
    /** Выпуск всего с производства, шт. */
    private Integer released;
    /** Количество брака в выпуске, шт. */
    private Integer defect;
    /** Доля брака = брак / выпуск × 100 */
    private BigDecimal percent;
    /** Раскрытие субъекта для pop-up, по убыванию количества брака */
    private List<DashDefectItemDTO> items;
}
