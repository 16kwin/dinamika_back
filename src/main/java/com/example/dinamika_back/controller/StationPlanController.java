package com.example.dinamika_back.controller;

import com.example.dinamika_back.config.security.AppRoles;
import com.example.dinamika_back.dto.StationPlanDTO;
import com.example.dinamika_back.dto.StationPlanPositionsRequest;
import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.service.StationPlanException;
import com.example.dinamika_back.service.StationPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.security.Principal;
import java.util.Map;

/**
 * Интерактивная карта станций: план цеха и расстановка станций. Требует входа (SecurityConfig);
 * смотреть могут все роли, менять расстановку и картинку — только администратор и директор.
 *
 * <p>Без функции {@code map.plan} в потолке контроллер и {@link StationPlanService} не создаются (L3);
 * хранилище файлов плана остаётся — его использует удаление цеха (WorkshopPlanFilesListener).
 */
@ConditionalOnFeature(Feature.MAP_PLAN)
@RestController
@RequestMapping("/api/station-plans")
@RequiredArgsConstructor
public class StationPlanController {

    private final StationPlanService service;

    /** План цеха: версия, картинка (или null — автоматическая схема) и позиции станций */
    @GetMapping("/workshops/{workshopId}")
    public StationPlanDTO plan(@PathVariable Long workshopId) {
        return service.get(workshopId);
    }

    /**
     * Сохранить расстановку пакетом: { version, positions: [{ stationUid, x, y }] }; 409 — устаревшая версия.
     * Станции, которых в цехе уже нет, пропускаются и перечисляются в skippedStationUids ответа
     */
    @PutMapping("/workshops/{workshopId}/positions")
    public StationPlanDTO savePositions(@PathVariable Long workshopId,
                                        @RequestBody StationPlanPositionsRequest body,
                                        Principal principal) {
        requireManager();
        return service.savePositions(workshopId, body, userName(principal));
    }

    /** Загрузить или заменить картинку плана: PNG, JPEG или WebP до 20 МБ и 8192 px по стороне */
    @PostMapping(value = "/workshops/{workshopId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StationPlanDTO uploadImage(@PathVariable Long workshopId,
                                      @RequestParam("file") MultipartFile file,
                                      Principal principal) {
        requireManager();
        return service.uploadImage(workshopId, file, userName(principal));
    }

    /** Удалить картинку плана — карта вернётся к автоматической схеме цеха */
    @DeleteMapping("/workshops/{workshopId}/image")
    public StationPlanDTO deleteImage(@PathVariable Long workshopId, Principal principal) {
        requireManager();
        return service.deleteImage(workshopId, userName(principal));
    }

    /**
     * Картинка плана. Браузер хранит копию, но каждый раз сверяет её с сервером (no-cache + ETag): после выхода
     * картинку из кэша без входа не открыть, а неизменная картинка приходит ответом 304 без тела
     */
    @GetMapping("/workshops/{workshopId}/image")
    public ResponseEntity<Resource> image(@PathVariable Long workshopId) {
        StationPlanService.ImageFile file = service.image(workshopId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mime()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .eTag(file.tag())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file.path()));
    }

    private static void requireManager() {
        if (!AppRoles.currentIsManager()) {
            throw StationPlanException.forbidden("Менять карту станций может только администратор или директор");
        }
    }

    private static String userName(Principal principal) {
        return principal == null ? null : principal.getName();
    }

    // ==================== Ошибки → { "error": "..." } ====================

    @ExceptionHandler(StationPlanException.class)
    public ResponseEntity<Map<String, String>> handlePlan(StationPlanException e) {
        return ResponseEntity.status(e.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Некорректное тело запроса: ожидается JSON"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Некорректное значение параметра " + e.getName()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, String>> handleMissingFile(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Файл не выбран"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(Map.of("error", "Карту цеха одновременно изменил другой пользователь. Обновите карту и повторите."));
    }
}
