// CountryCrudController.java — ПОЛНЫЙ ФАЙЛ (добавлены все эндпоинты настроек)
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.CountryEventLogDto;
import com.example.dinamika_back.dto.CountryListResponse;
import com.example.dinamika_back.dto.SprCountryDTO;
import com.example.dinamika_back.service.CountryCrudService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/countries-crud")
@RequiredArgsConstructor
public class CountryCrudController {

    private final CountryCrudService countryCrudService;

    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<CountryListResponse> getAll(@RequestParam(required = false) Integer userId) {
        return ResponseEntity.ok(countryCrudService.getAllWithSettings(userId));
    }

    @GetMapping("/{uid}")
    public ResponseEntity<SprCountryDTO> getById(@PathVariable UUID uid) {
        return ResponseEntity.ok(countryCrudService.getById(uid));
    }

    @PostMapping
    public ResponseEntity<SprCountryDTO> create(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(countryCrudService.create(body.get("name")));
    }

    @PatchMapping("/{uid}")
    public ResponseEntity<SprCountryDTO> update(@PathVariable UUID uid, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(countryCrudService.update(uid, body.get("name")));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        countryCrudService.delete(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== Events ====================

    @GetMapping("/events")
    public ResponseEntity<List<CountryEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(countryCrudService.getAllEvents());
    }

    @GetMapping("/{uid}/events")
    public ResponseEntity<List<CountryEventLogDto>> getEvents(@PathVariable UUID uid) {
        return ResponseEntity.ok(countryCrudService.getEvents(uid));
    }

    // ==================== All Settings ====================

    @GetMapping("/settings")
    public ResponseEntity<Map<String, String>> getAllSettings(@RequestParam Integer userId) {
        Map<String, String> settings = Map.of(
                "columnsJson", countryCrudService.getColumnsJson(userId) != null
                        ? countryCrudService.getColumnsJson(userId) : "{}",
                "filtersJson", countryCrudService.getFiltersJson(userId),
                "sortJson", countryCrudService.getSortJson(userId)
        );
        return ResponseEntity.ok(settings);
    }

    // ==================== Column Settings ====================

    @GetMapping("/columns-settings")
    public ResponseEntity<String> getColumnsSettings(@RequestParam Integer userId) {
        String json = countryCrudService.getColumnsJson(userId);
        return ResponseEntity.ok(json != null ? json : "{}");
    }

    @PatchMapping("/columns-settings")
    public ResponseEntity<Void> saveColumnsSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String columnsJson = (String) body.get("columnsJson");
        countryCrudService.saveColumnsJson(userId, columnsJson);
        return ResponseEntity.ok().build();
    }

    // ==================== Filters Settings ====================

    @GetMapping("/filters-settings")
    public ResponseEntity<String> getFiltersSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(countryCrudService.getFiltersJson(userId));
    }

    @PatchMapping("/filters-settings")
    public ResponseEntity<Void> saveFiltersSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String filtersJson = (String) body.get("filtersJson");
        countryCrudService.saveFiltersJson(userId, filtersJson);
        return ResponseEntity.ok().build();
    }

    // ==================== Sort Settings ====================

    @GetMapping("/sort-settings")
    public ResponseEntity<String> getSortSettings(@RequestParam Integer userId) {
        return ResponseEntity.ok(countryCrudService.getSortJson(userId));
    }

    @PatchMapping("/sort-settings")
    public ResponseEntity<Void> saveSortSettings(@RequestParam Integer userId, @RequestBody Map<String, Object> body) {
        String sortJson = (String) body.get("sortJson");
        countryCrudService.saveSortJson(userId, sortJson);
        return ResponseEntity.ok().build();
    }
}