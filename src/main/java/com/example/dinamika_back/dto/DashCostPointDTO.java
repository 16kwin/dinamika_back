package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Точка графика затрат: дата, план и факт, руб.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashCostPointDTO {
    private LocalDate date;
    private BigDecimal plan;
    private BigDecimal fact;
}
