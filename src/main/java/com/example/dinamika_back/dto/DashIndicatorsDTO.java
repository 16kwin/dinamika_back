package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Показатели затрат за период: закупки и выдача, руб.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashIndicatorsDTO {
    private BigDecimal purchases;
    private BigDecimal issue;
}
