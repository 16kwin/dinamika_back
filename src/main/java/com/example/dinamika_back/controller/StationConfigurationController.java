// StationConfigurationController.java — ПОЛНЫЙ ФАЙЛ (с getAllWithSettings)
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.StationConfigurationService;
import com.example.dinamika_back.service.StationConfigurationColumnSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/station-configurations")
@RequiredArgsConstructor
public class StationConfigurationController {

    private final StationConfigurationService configurationService;
    private final StationConfigurationColumnSettingsService columnSettingsService;

    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<StationConfigurationListResponse> getAll(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) UUID modelId) {
        if (modelId != null) {
            List<StationConfigurationDto> byModel = configurationService.getByModelId(modelId);
            StationConfigurationListResponse response = new StationConfigurationListResponse();
            response.setColumns(List.of("name", "modelName"));
            response.setData(byModel);
            response.setColumnWidths(Map.of());
            response.setRequiredColumns(List.of("name"));
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.ok(configurationService.getAllWithSettings(userId));
    }

    @GetMapping("/{uid}")
    public ResponseEntity<StationConfigurationDto> getById(@PathVariable UUID uid) {
        return ResponseEntity.ok(configurationService.getById(uid));
    }

    @PostMapping
    public ResponseEntity<StationConfigurationDto> create(@RequestBody CreateStationConfigurationRequest request) {
        return ResponseEntity.ok(configurationService.create(request));
    }

    @PatchMapping("/{uid}")
    public ResponseEntity<StationConfigurationDto> update(
            @PathVariable UUID uid,
            @RequestBody UpdateStationConfigurationRequest request) {
        return ResponseEntity.ok(configurationService.update(uid, request));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        configurationService.delete(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Events ====================

    @GetMapping("/events")
    public ResponseEntity<List<StationConfigurationEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(configurationService.getAllEvents());
    }

    @GetMapping("/{uid}/events")
    public ResponseEntity<List<StationConfigurationEventLogDto>> getEvents(@PathVariable UUID uid) {
        return ResponseEntity.ok(configurationService.getEvents(uid));
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