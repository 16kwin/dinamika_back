package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Ответ GET /api/dashboard/audit — панель «Аудитор» за период: инциденты и выдачи сверх нормы
 * относительно числа операций и лента закупок с завышенной ценой.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardAuditResponse {
    private LocalDate from;
    private LocalDate to;
    /** Инциденты из всех операций периода */
    private DashAuditMetricDTO incidents;
    /** Выдачи сверх нормы из всех операций периода */
    private DashAuditMetricDTO overNorm;
    /** Лимит превышения нормативной цены, по которому отобрана лента overpriced, % */
    private Integer limitPercent;
    /** Заказы периода с ценой выше нормативной больше чем на limitPercent, самые поздние первыми */
    private List<DashAuditOverpricedDTO> overpriced;
}
