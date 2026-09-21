package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Суммы прохождения контроля качества за период (карточка «Производство»). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashQcSumDTO {
    private BigDecimal passed;
    private BigDecimal waiting;
    private BigDecimal failed;
}
