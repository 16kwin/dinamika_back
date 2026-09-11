package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Сумма затрат по виду номенклатуры за период.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashTypeAmountDTO {
    private String key;
    private String name;
    private BigDecimal amount;

    /** Конструктор для JPQL-проекции (ключ + сумма); название подставляет сервис */
    public DashTypeAmountDTO(String key, BigDecimal amount) {
        this.key = key;
        this.amount = amount;
    }
}
