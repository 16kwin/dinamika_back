// LocationCrudController.java — ПОЛНЫЙ ФАЙЛ (с settings/sort/filters)
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.LocationEventLogDto;
import com.example.dinamika_back.dto.LocationFlatDto;
import com.example.dinamika_back.dto.LocationListResponse;
import com.example.dinamika_back.service.LocationCrudService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/locations-crud")
@RequiredArgsConstructor
public class LocationCrudController {

    private final LocationCrudService locationCrudService;

    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<LocationListResponse> getAll(@RequestParam(required = false) Integer userId) {
        return ResponseEntity.ok(locationCrudService.getAllWithSettings(userId));
    }

    @GetMapping("/{uid}")
    public ResponseEntity<LocationFlatDto> getById(@PathVariable UUID uid) {
        return ResponseEntity.ok(locationCrudService.getById(uid));
    }

    @PostMapping
    public ResponseEntity<LocationFlatDto> create(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(locationCrudService.create(body.get("name")));
    }

    @PatchMapping("/{uid}")
    public ResponseEntity<LocationFlatDto> update(@PathVariable UUID uid, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(locationCrudService.update(uid, body.get("name")));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        locationCrudService.delete(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Events ====================

    @GetMapping("/events")
    public ResponseEntity<List<LocationEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(locationCrudService.getAllEvents());
    }

    @GetMapping("/{uid}/events")
    public ResponseEntity<List<LocationEventLogDto>> getEvents(@PathVariable UUID uid) {
        return ResponseEntity.ok(locationCrudService.getEvents(uid));
    }

    // ==================== All Settings ====================

    @GetMapping("/settings")
    public ResponseEntity<Map<String, String>> getAllSettings(@RequestParam Integer userId) {
        Map<String, String> settings = Map.of(
                "columnsJson", locationCrudService.getColumnsJson(userId) != null
                        ? locationCrudService.getColumnsJson(userId) : "{}",
                "filtersJson", locationCrudService.getFiltersJson(userId),
                "sortJson", locationCrudService.getSortJson(userId)
        );
        return ResponseEntity.ok(settings);
    }

    // ==================== Column Settings ====================

    @GetMapping("/columns-settings")
    public ResponseEntity<String> getColumnsSettings(@RequestParam Integer userId) {
        String json = locationCrudService.getColumnsJson(userId);
        return ResponseEntity.ok(json != null ? json : "{}");
    }

    @PatchMapping("/columns-settings")
    public ResponseEntity<Void> saveColumnsSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String columnsJson = (String) body.get("columnsJson");
        locationCrudService.saveColumnsJson(userId, columnsJson);
        return ResponseEntity.ok().build();
    }

    // ==================== Filters Settings ====================

    @GetMapping("/filters-settings")
    public ResponseEntity<String> getFiltersSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(locationCrudService.getFiltersJson(userId));
    }

    @PatchMapping("/filters-settings")
    public ResponseEntity<Void> saveFiltersSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String filtersJson = (String) body.get("filtersJson");
        locationCrudService.saveFiltersJson(userId, filtersJson);
        return ResponseEntity.ok().build();
    }

    // ==================== Sort Settings ====================

    @GetMapping("/sort-settings")
    public ResponseEntity<String> getSortSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(locationCrudService.getSortJson(userId));
    }

    @PatchMapping("/sort-settings")
    public ResponseEntity<Void> saveSortSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String sortJson = (String) body.get("sortJson");
        locationCrudService.saveSortJson(userId, sortJson);
        return ResponseEntity.ok().build();
    }
}