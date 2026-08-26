package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.TypeMaterialColumnSettingsService;
import com.example.dinamika_back.service.TypeMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/type-materials")
@RequiredArgsConstructor
public class TypeMaterialController {

    private final TypeMaterialService typeMaterialService;
    private final TypeMaterialColumnSettingsService columnSettingsService;

    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<TypeMaterialListResponse> getAll(@RequestParam(required = false) Integer userId) {
        return ResponseEntity.ok(typeMaterialService.getAllWithSettings(userId));
    }

    @PostMapping
    public ResponseEntity<SprTypeMaterialDTO> create(@RequestBody CreateTypeMaterialRequest request) {
        return ResponseEntity.ok(typeMaterialService.create(request));
    }

    @PatchMapping("/{uid}")
    public ResponseEntity<SprTypeMaterialDTO> update(@PathVariable UUID uid, @RequestBody UpdateTypeMaterialRequest request) {
        return ResponseEntity.ok(typeMaterialService.update(uid, request));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        typeMaterialService.delete(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Events ====================

    @GetMapping("/events")
    public ResponseEntity<List<TypeMaterialEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(typeMaterialService.getAllEvents());
    }

    @GetMapping("/{uid}/events")
    public ResponseEntity<List<TypeMaterialEventLogDto>> getEvents(@PathVariable UUID uid) {
        return ResponseEntity.ok(typeMaterialService.getEvents(uid));
    }

    // ==================== Settings ====================

    @GetMapping("/settings")
    public ResponseEntity<Map<String, String>> getAllSettings(@RequestParam Integer userId) {
        Map<String, String> settings = Map.of(
                "columnsJson", columnSettingsService.getColumnsJson(userId) != null
                        ? columnSettingsService.getColumnsJson(userId) : "{}",
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
        String sortJson = (String) body.get("sortJson");
        columnSettingsService.saveAllJson(userId, columnsJson, sortJson);
        return ResponseEntity.ok().build();
    }
}