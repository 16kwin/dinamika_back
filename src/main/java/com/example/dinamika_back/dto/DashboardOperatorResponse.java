package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Ответ GET /api/dashboard/operator — панель «Оператор склада»: показатели на последнюю дату,
 * остатки по станциям и ленты заказов и событий.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOperatorResponse {
    /** Дата, на которую взяты показатели */
    private LocalDate statDate;
    /** Четыре карточки-показателя в порядке вывода */
    private List<DashOperatorMetricDTO> metrics;
    /** График «Критические и минимальные остатки по станциям» */
    private List<DashStationDTO> stations;
    /** Лента «Заказы на поставку» */
    private List<DashOperatorEventDTO> orders;
    /** Лента «Экран событий» */
    private List<DashOperatorEventDTO> events;
}
