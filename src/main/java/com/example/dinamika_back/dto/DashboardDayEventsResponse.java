package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Ответ GET /api/dashboard/day-events — «Экран событий текущего дня» для одного источника:
 * колонки «В работе» и «Завершено».
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDayEventsResponse {
    /** Текущая дата */
    private LocalDate date;
    /** operator | control | release | overpriced */
    private String source;
    /** Название источника для заголовка экрана */
    private String sourceName;
    /** Колонка «В работе», самые поздние первыми */
    private List<DashDayEventDTO> inWork;
    /** Колонка «Завершено», самые поздние первыми */
    private List<DashDayEventDTO> done;
}
