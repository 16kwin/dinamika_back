package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Суммы выпуска и брака за период (карточка «Показатели качества»). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashQualitySumDTO {
    private BigDecimal released;
    private BigDecimal defect;
}
