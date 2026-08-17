// StationManufacturerController.java — ПОЛНЫЙ ФАЙЛ (с getAllWithSettings)
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.StationManufacturerService;
import com.example.dinamika_back.service.StationManufacturerColumnSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/station-manufacturers")
@RequiredArgsConstructor
public class StationManufacturerController {

    private final StationManufacturerService manufacturerService;
    private final StationManufacturerColumnSettingsService columnSettingsService;

    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<StationManufacturerListResponse> getAll(@RequestParam(required = false) Integer userId) {
        return ResponseEntity.ok(manufacturerService.getAllWithSettings(userId));
    }

    @GetMapping("/{uid}")
    public ResponseEntity<StationManufacturerDto> getById(@PathVariable UUID uid) {
        return ResponseEntity.ok(manufacturerService.getById(uid));
    }

    @PostMapping
    public ResponseEntity<StationManufacturerDto> create(@RequestBody CreateStationManufacturerRequest request) {
        return ResponseEntity.ok(manufacturerService.create(request));
    }

    @PatchMapping("/{uid}")
    public ResponseEntity<StationManufacturerDto> update(@PathVariable UUID uid, @RequestBody UpdateStationManufacturerRequest request) {
        return ResponseEntity.ok(manufacturerService.update(uid, request));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        manufacturerService.delete(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Events ====================

    @GetMapping("/events")
    public ResponseEntity<List<StationManufacturerEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(manufacturerService.getAllEvents());
    }

    @GetMapping("/{uid}/events")
    public ResponseEntity<List<StationManufacturerEventLogDto>> getEvents(@PathVariable UUID uid) {
        return ResponseEntity.ok(manufacturerService.getEvents(uid));
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