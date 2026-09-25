package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Ответ GET /api/dashboard/control-events — лента контроля качества для панелей «Контролер»
 * (scope=section) и «Главный контролер» (scope=enterprise).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardControlEventsResponse {
    /** section | enterprise */
    private String scope;
    /** Подпись ленты: подразделение контролёра или «Предприятие» */
    private String department;
    /** События, самые свежие первыми */
    private List<DashControlEventDTO> items;
}
