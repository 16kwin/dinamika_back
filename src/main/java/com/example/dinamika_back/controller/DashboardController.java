package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.DashboardAuditResponse;
import com.example.dinamika_back.dto.DashboardControlEventsResponse;
import com.example.dinamika_back.dto.DashboardDayEventsResponse;
import com.example.dinamika_back.dto.DashboardEconomicResponse;
import com.example.dinamika_back.dto.DashboardOperatorResponse;
import com.example.dinamika_back.dto.DashboardPurchaseGraphResponse;
import com.example.dinamika_back.dto.DashboardQualityResponse;
import com.example.dinamika_back.dto.DashboardSettingsDTO;
import com.example.dinamika_back.service.DashboardAuditService;
import com.example.dinamika_back.service.DashboardControlEventService;
import com.example.dinamika_back.service.DashboardDayEventService;
import com.example.dinamika_back.service.DashboardOperatorService;
import com.example.dinamika_back.service.DashboardPurchaseGraphService;
import com.example.dinamika_back.service.DashboardQualityService;
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
 * Информационные панели главной страницы: «Экономический блок», «Показатели», «Оператор склада»,
 * панели ролей «Контролер», «Главный контролер», «Начальник цеха», «Служба закупа», «Аудитор»
 * и отдельные экраны «Граф закупок» и «Экран событий текущего дня» (permitAll, см. SecurityConfig).
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardQualityService dashboardQualityService;
    private final DashboardOperatorService dashboardOperatorService;
    private final DashboardPurchaseGraphService dashboardPurchaseGraphService;
    private final DashboardAuditService dashboardAuditService;
    private final DashboardControlEventService dashboardControlEventService;
    private final DashboardDayEventService dashboardDayEventService;

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

    /** Панель «Показатели» (топ-менеджмент): расход объема, уровень брака, качество, производство */
    @GetMapping("/quality")
    public ResponseEntity<DashboardQualityResponse> getQuality(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDate dateFrom = parseDate("from", from);
        LocalDate dateTo = parseDate("to", to);
        if (dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("Дата from не может быть позже даты to");
        }
        return ResponseEntity.ok(dashboardQualityService.getQuality(dateFrom, dateTo));
    }

    /** Панель «Оператор склада»: показатели на последнюю дату, остатки станций и ленты — без периода */
    @GetMapping("/operator")
    public ResponseEntity<DashboardOperatorResponse> getOperator() {
        return ResponseEntity.ok(dashboardOperatorService.getOperator());
    }

    /**
     * «Граф закупок» (панель «Служба закупа» и отдельный экран): справочники номенклатуры и поставщиков,
     * закупки за период по парам номенклатура × поставщик и связи поставщиков
     */
    @GetMapping("/purchase-graph")
    public ResponseEntity<DashboardPurchaseGraphResponse> getPurchaseGraph(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDate dateFrom = parseDate("from", from);
        LocalDate dateTo = parseDate("to", to);
        if (dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("Дата from не может быть позже даты to");
        }
        return ResponseEntity.ok(dashboardPurchaseGraphService.getPurchaseGraph(dateFrom, dateTo));
    }

    /** Панель «Аудитор»: инциденты и выдачи сверх нормы за период, лента закупок с завышенной ценой */
    @GetMapping("/audit")
    public ResponseEntity<DashboardAuditResponse> getAudit(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDate dateFrom = parseDate("from", from);
        LocalDate dateTo = parseDate("to", to);
        if (dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("Дата from не может быть позже даты to");
        }
        return ResponseEntity.ok(dashboardAuditService.getAudit(dateFrom, dateTo));
    }

    /** Лента контроля качества: scope=section — участок контролёра (по умолчанию), enterprise — предприятие */
    @GetMapping("/control-events")
    public ResponseEntity<DashboardControlEventsResponse> getControlEvents(
            @RequestParam(required = false) String scope) {
        return ResponseEntity.ok(dashboardControlEventService.getControlEvents(scope));
    }

    /** «Экран событий текущего дня»: source = operator (по умолчанию) | control | release | overpriced */
    @GetMapping("/day-events")
    public ResponseEntity<DashboardDayEventsResponse> getDayEvents(
            @RequestParam(required = false) String source) {
        return ResponseEntity.ok(dashboardDayEventService.getDayEvents(source));
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
