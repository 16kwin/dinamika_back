package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Суммы операций, инцидентов и выдач сверх нормы за период (панель «Аудитор»). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashAuditSumDTO {
    private Long operations;
    private Long incidents;
    private Long overNorm;
}
