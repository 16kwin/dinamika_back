package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.AttributeGroupColumnSettingsService;
import com.example.dinamika_back.service.AttributeGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/attribute-groups")
@RequiredArgsConstructor
public class AttributeGroupController {

    private final AttributeGroupService attributeGroupService;
    private final AttributeGroupColumnSettingsService columnSettingsService;

    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<AttributeGroupListResponse> getAll(@RequestParam(required = false) Integer userId) {
        return ResponseEntity.ok(attributeGroupService.getAllWithSettings(userId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateAttributeGroupRequest request) {
        return ResponseEntity.ok(attributeGroupService.create(request));
    }

    @PatchMapping("/{uid}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID uid, @RequestBody UpdateAttributeGroupRequest request) {
        return ResponseEntity.ok(attributeGroupService.update(uid, request));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        attributeGroupService.delete(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Events ====================

    @GetMapping("/events")
    public ResponseEntity<List<AttributeGroupEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(attributeGroupService.getAllEvents());
    }

    @GetMapping("/{uid}/events")
    public ResponseEntity<List<AttributeGroupEventLogDto>> getEvents(@PathVariable UUID uid) {
        return ResponseEntity.ok(attributeGroupService.getEvents(uid));
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