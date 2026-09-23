package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Карточка «Производство»: прохождение контроля качества за период. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashQcDTO {
    private Integer passed;
    private Integer waiting;
    private Integer failed;
    /** Всего выпуск с производства = сумма трёх состояний, шт. */
    private Integer total;
    private BigDecimal passedPercent;
    private BigDecimal waitingPercent;
    private BigDecimal failedPercent;
}
