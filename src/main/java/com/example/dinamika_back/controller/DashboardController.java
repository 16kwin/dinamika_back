package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.DashboardEconomicResponse;
import com.example.dinamika_back.dto.DashboardSettingsDTO;
import com.example.dinamika_back.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Панель «Экономический блок» (permitAll, см. SecurityConfig).
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    // ==================== Данные панели ====================

    /** Все карточки панели за период; userId — для выбранных пользователем видов (без него — по умолчанию) */
    @GetMapping("/economic")
    public ResponseEntity<DashboardEconomicResponse> getEconomic(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer userId) {
        LocalDate dateFrom = parseDate("from", from);
        LocalDate dateTo = parseDate("to", to);
        if (dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("Дата from не может быть позже даты to");
        }
        return ResponseEntity.ok(dashboardService.getEconomic(dateFrom, dateTo, userId));
    }

    // ==================== Settings ====================

    @GetMapping("/economic/settings")
    public ResponseEntity<DashboardSettingsDTO> getSettings(@RequestParam(required = false) Integer userId) {
        return ResponseEntity.ok(dashboardService.getSettings(userId));
    }

    @PatchMapping("/economic/settings")
    public ResponseEntity<Map<String, Object>> saveSettings(@RequestParam(required = false) Integer userId,
            @RequestBody DashboardSettingsDTO body) {
        dashboardService.saveSettings(userId, body);
        return ResponseEntity.ok(Map.of());
    }

    // ==================== Ошибки → 400 { "error": "..." } ====================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        String message = e.getMessage() != null ? e.getMessage() : "Некорректный запрос";
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    /** Невалидный JSON или отсутствующее тело запроса */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error",
                "Некорректное тело запроса: ожидается JSON вида { \"barTypes\": [...], \"radarTypes\": [...] }"));
    }

    /** Нечисловой userId и т.п. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Некорректное значение параметра " + e.getName()));
    }

    /** Разбор даты YYYY-MM-DD из обязательного параметра */
    private static LocalDate parseDate(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Параметр " + name + " обязателен (формат YYYY-MM-DD)");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Неверный формат даты " + name + ": '" + value + "' (ожидается YYYY-MM-DD)");
        }
    }
}
