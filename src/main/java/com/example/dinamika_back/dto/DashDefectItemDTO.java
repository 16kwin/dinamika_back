package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Строка pop-up: подразделение (для вида «По деталям») или номенклатура (для вида «По подразделениям»).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashDefectItemDTO {
    private String key;
    private String name;
    /** Выпуск всего, шт. */
    private Integer released;
    /** Количество брака, шт. */
    private Integer defect;
    /** Доля брака = брак / выпуск × 100 */
    private BigDecimal percent;
}
