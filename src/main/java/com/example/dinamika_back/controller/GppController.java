package com.example.dinamika_back.controller;

import com.example.dinamika_back.config.security.AppRoles;
import com.example.dinamika_back.dto.GppDocumentDTO;
import com.example.dinamika_back.dto.GppSaveRequest;
import com.example.dinamika_back.service.GppException;
import com.example.dinamika_back.service.GppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * Документ «График подготовки производства (ГПП по предприятию)». Требует входа (SecurityConfig);
 * смотреть могут все роли, сохранять — только администратор и директор.
 */
@Slf4j
@RestController
@RequestMapping("/api/gpp")
@RequiredArgsConstructor
public class GppController {

    private final GppService service;

    /** Документ со строками в порядке обхода дерева; canEdit — может ли текущий пользователь сохранять */
    @GetMapping
    public GppDocumentDTO get() {
        return service.get(AppRoles.currentIsManager());
    }

    /**
     * Сохранить: { version, gppType, autoCalc, periodFrom, periodTo, rows } — полная замена строк; 409 — устаревшая
     * версия, 503 — не дождались очереди: документ дольше GppService.LOCK_TIMEOUT записывает другой пользователь
     */
    @PutMapping
    public GppDocumentDTO save(@RequestBody GppSaveRequest body, Principal principal) {
        if (!AppRoles.currentIsManager()) {
            throw GppException.forbidden("Сохранять график подготовки производства может только администратор или директор");
        }
        return service.save(body, principal == null ? null : principal.getName());
    }

    // ==================== Ошибки → { "message": "..." } ====================

    @ExceptionHandler(GppException.class)
    public ResponseEntity<Map<String, String>> handleGpp(GppException e) {
        return ResponseEntity.status(e.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", "Некорректное тело запроса: ожидается JSON документа ГПП"));
    }

    /** Ограничения базы после проверки сервиса сработать не должны — значит, данные графика противоречивы */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("ГПП: база отклонила сохранение: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", "Данные графика не прошли проверку базы данных. Обновите документ и повторите правку."));
    }

    /** Сохранение не дождалось блокировки документа (GppService.LOCK_TIMEOUT): график записывает другой пользователь */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleLock(PessimisticLockingFailureException e) {
        log.warn("ГПП: сохранение не дождалось блокировки документа: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", "График сейчас записывает другой пользователь. Повторите запись через минуту."));
    }

    /** Непредвиденная ошибка: ответ по контракту { message } без подробностей, подробности — в журнал */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(RuntimeException e) {
        log.error("ГПП: непредвиденная ошибка", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", "Внутренняя ошибка сервера при обработке графика. Повторите попытку позже."));
    }
}
