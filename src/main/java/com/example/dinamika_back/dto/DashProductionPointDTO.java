package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Точка графика расхода объема номенклатуры: дата, план и факт расхода ТМЦ. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashProductionPointDTO {
    private LocalDate date;
    private BigDecimal plan;
    private BigDecimal fact;
}
