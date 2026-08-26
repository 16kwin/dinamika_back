package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.TypePurposeColumnSettingsService;
import com.example.dinamika_back.service.TypePurposeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/type-purposes")
@RequiredArgsConstructor
public class TypePurposeController {

    private final TypePurposeService typePurposeService;
    private final TypePurposeColumnSettingsService columnSettingsService;

    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<TypePurposeListResponse> getAll(@RequestParam(required = false) Integer userId) {
        return ResponseEntity.ok(typePurposeService.getAllWithSettings(userId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateTypePurposeRequest request) {
        return ResponseEntity.ok(typePurposeService.create(request));
    }

    @PatchMapping("/{uid}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID uid, @RequestBody UpdateTypePurposeRequest request) {
        return ResponseEntity.ok(typePurposeService.update(uid, request));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        typePurposeService.delete(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Events ====================

    @GetMapping("/events")
    public ResponseEntity<List<TypePurposeEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(typePurposeService.getAllEvents());
    }

    @GetMapping("/{uid}/events")
    public ResponseEntity<List<TypePurposeEventLogDto>> getEvents(@PathVariable UUID uid) {
        return ResponseEntity.ok(typePurposeService.getEvents(uid));
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