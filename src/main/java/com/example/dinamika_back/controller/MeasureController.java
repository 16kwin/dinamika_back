package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.MeasureColumnSettingsService;
import com.example.dinamika_back.service.MeasureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/measures-crud")
@RequiredArgsConstructor
public class MeasureController {

    private final MeasureService measureService;
    private final MeasureColumnSettingsService columnSettingsService;

    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<MeasureListResponse> getAll(@RequestParam(required = false) Integer userId) {
        return ResponseEntity.ok(measureService.getAllWithSettings(userId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateMeasureRequest request) {
        return ResponseEntity.ok(measureService.create(request));
    }

    @PatchMapping("/{uid}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID uid, @RequestBody UpdateMeasureRequest request) {
        return ResponseEntity.ok(measureService.update(uid, request));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        measureService.delete(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Events ====================

    @GetMapping("/events")
    public ResponseEntity<List<MeasureEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(measureService.getAllEvents());
    }

    @GetMapping("/{uid}/events")
    public ResponseEntity<List<MeasureEventLogDto>> getEvents(@PathVariable UUID uid) {
        return ResponseEntity.ok(measureService.getEvents(uid));
    }

    // ==================== Settings ====================

    @GetMapping("/settings")
    public ResponseEntity<Map<String, String>> getAllSettings(@RequestParam Integer userId) {
        Map<String, String> settings = Map.of(
                "columnsJson", columnSettingsService.getColumnsJson(userId) != null
                        ? columnSettingsService.getColumnsJson(userId) : "{}",
                "filtersJson", columnSettingsService.getFiltersJson(userId),
                "sortJson", columnSettingsService.getSortJson(userId)
        );
        return ResponseEntity.ok(settings);
    }

    @GetMapping("/columns-settings")
    public ResponseEntity<String> getColumnsSettings(@RequestParam Integer userId) {
        String json = columnSettingsService.getColumnsJson(userId);
        return ResponseEntity.ok(json != null ? json : "{}");
    }

    @PatchMapping("/columns-settings")
    public ResponseEntity<Void> saveColumnsSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String columnsJson = (String) body.get("columnsJson");
        columnSettingsService.saveColumnsJson(userId, columnsJson);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/filters-settings")
    public ResponseEntity<String> getFiltersSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(columnSettingsService.getFiltersJson(userId));
    }

    @PatchMapping("/filters-settings")
    public ResponseEntity<Void> saveFiltersSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String filtersJson = (String) body.get("filtersJson");
        columnSettingsService.saveFiltersJson(userId, filtersJson);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sort-settings")
    public ResponseEntity<String> getSortSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(columnSettingsService.getSortJson(userId));
    }

    @PatchMapping("/sort-settings")
    public ResponseEntity<Void> saveSortSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String sortJson = (String) body.get("sortJson");
        columnSettingsService.saveSortJson(userId, sortJson);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/settings")
    public ResponseEntity<Void> saveAllSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String columnsJson = (String) body.get("columnsJson");
        String filtersJson = (String) body.get("filtersJson");
        String sortJson = (String) body.get("sortJson");
        columnSettingsService.saveAllJson(userId, columnsJson, filtersJson, sortJson);
        return ResponseEntity.ok().build();
    }
}