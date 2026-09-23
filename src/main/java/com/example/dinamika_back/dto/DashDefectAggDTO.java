package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Агрегат выпуска и брака за период по паре субъект × элемент раскрытия. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashDefectAggDTO {
    private String subjectKey;
    private String itemKey;
    private BigDecimal released;
    private BigDecimal defect;
}
