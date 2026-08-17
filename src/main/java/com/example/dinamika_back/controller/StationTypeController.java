// StationTypeController.java — ПОЛНЫЙ ФАЙЛ (с getAllWithSettings)
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.StationTypeService;
import com.example.dinamika_back.service.StationTypeColumnSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/station-types")
@RequiredArgsConstructor
public class StationTypeController {

    private final StationTypeService stationTypeService;
    private final StationTypeColumnSettingsService columnSettingsService;

    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<StationTypeListResponse> getAll(@RequestParam(required = false) Integer userId) {
        return ResponseEntity.ok(stationTypeService.getAllWithSettings(userId));
    }

    @GetMapping("/{uid}")
    public ResponseEntity<StationTypeDto> getById(@PathVariable UUID uid) {
        return ResponseEntity.ok(stationTypeService.getById(uid));
    }

    @PostMapping
    public ResponseEntity<StationTypeDto> create(@RequestBody CreateStationTypeRequest request) {
        return ResponseEntity.ok(stationTypeService.create(request));
    }

    @PatchMapping("/{uid}")
    public ResponseEntity<StationTypeDto> update(@PathVariable UUID uid, @RequestBody UpdateStationTypeRequest request) {
        return ResponseEntity.ok(stationTypeService.update(uid, request));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        stationTypeService.delete(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Events ====================

    @GetMapping("/events")
    public ResponseEntity<List<StationTypeEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(stationTypeService.getAllEvents());
    }

    @GetMapping("/{uid}/events")
    public ResponseEntity<List<StationTypeEventLogDto>> getEvents(@PathVariable UUID uid) {
        return ResponseEntity.ok(stationTypeService.getEvents(uid));
    }

    // ==================== All Settings ====================

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

    // ==================== Column Settings ====================

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

    // ==================== Filters Settings ====================

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

    // ==================== Sort Settings ====================

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
}