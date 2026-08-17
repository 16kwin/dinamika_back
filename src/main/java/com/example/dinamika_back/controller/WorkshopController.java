// WorkshopController.java — ПОЛНЫЙ ФАЙЛ (с getAllWithSettings)
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.WorkshopService;
import com.example.dinamika_back.service.WorkshopColumnSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workshops")
@RequiredArgsConstructor
public class WorkshopController {

    private final WorkshopService workshopService;
    private final WorkshopColumnSettingsService columnSettingsService;

    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<WorkshopListResponse> getAll(@RequestParam(required = false) Integer userId,
                                                        @RequestParam(required = false) Long enterpriseId) {
        if (enterpriseId != null) {
            List<WorkshopFlatDto> byEnterprise = workshopService.getByEnterpriseId(enterpriseId);
            WorkshopListResponse response = new WorkshopListResponse();
            response.setColumns(List.of("name", "description", "address", "enterpriseName", "holdingName", "locationName"));
            response.setData(byEnterprise);
            response.setColumnWidths(Map.of());
            response.setRequiredColumns(List.of("name"));
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.ok(workshopService.getAllWithSettings(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkshopFlatDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(workshopService.getById(id));
    }

    @PostMapping
    public ResponseEntity<WorkshopFlatDto> create(@RequestBody CreateWorkshopRequest request) {
        return ResponseEntity.ok(workshopService.create(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<WorkshopFlatDto> update(@PathVariable Long id, @RequestBody UpdateWorkshopRequest request) {
        return ResponseEntity.ok(workshopService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        workshopService.delete(id);
        return ResponseEntity.ok().build();
    }

    // ==================== Events ====================

    @GetMapping("/events")
    public ResponseEntity<List<WorkshopEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(workshopService.getAllEvents());
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<List<WorkshopEventLogDto>> getEvents(@PathVariable Long id) {
        return ResponseEntity.ok(workshopService.getEvents(id));
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