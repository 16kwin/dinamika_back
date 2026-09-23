package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Карточка «Показатели качества»: выпуск, брак и средний уровень брака за период. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashQualityIndicatorsDTO {
    /** Выпуск (продукция), шт. */
    private Integer released;
    /** Брак, шт. */
    private Integer defect;
    /** Всего выпуск с производства = выпуск + брак, шт. */
    private Integer total;
    /** Средний уровень брака = брак / всего выпуск × 100 */
    private BigDecimal defectPercent;
}
