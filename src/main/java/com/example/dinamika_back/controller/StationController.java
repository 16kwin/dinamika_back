// StationController.java — ПОЛНЫЙ ФАЙЛ (добавлены эндпоинты для фильтров и сортировки)
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.DocPattern;
import com.example.dinamika_back.model.Station;
import com.example.dinamika_back.repository.DocPatternRepository;
import com.example.dinamika_back.repository.StationRepository;
import com.example.dinamika_back.service.StationColumnSettingsService;
import com.example.dinamika_back.service.StationCrudService;
import com.example.dinamika_back.service.StationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/stations")
public class StationController {

    private final StationService stationService;
    private final StationCrudService stationCrudService;
    private final StationColumnSettingsService columnSettingsService;
    private final StationRepository stationRepository;
    private final DocPatternRepository docPatternRepository;

    @Autowired
    public StationController(StationService stationService,
                             StationCrudService stationCrudService,
                             StationColumnSettingsService columnSettingsService,
                             StationRepository stationRepository,
                             DocPatternRepository docPatternRepository) {
        this.stationService = stationService;
        this.stationCrudService = stationCrudService;
        this.columnSettingsService = columnSettingsService;
        this.stationRepository = stationRepository;
        this.docPatternRepository = docPatternRepository;
    }

    // ==================== Мониторинг ====================

    @PostMapping("/static/filtered")
    public ResponseEntity<List<StationStaticDto>> getFilteredStaticStations(@RequestBody UserFilterDTO filters) {
        return ResponseEntity.ok(stationService.getFilteredStaticStations(filters));
    }

    @PostMapping("/dynamic/filtered")
    public ResponseEntity<List<StationDynamicDto>> getFilteredDynamicStations(@RequestBody UserFilterDTO filters) {
        return ResponseEntity.ok(stationService.getFilteredDynamicStations(filters));
    }

    @GetMapping("/static")
    public ResponseEntity<List<StationStaticDto>> getAllStaticStations() {
        return ResponseEntity.ok(stationService.getAllStaticStations());
    }

    @GetMapping("/dynamic")
    public ResponseEntity<List<StationDynamicDto>> getAllDynamicStations() {
        return ResponseEntity.ok(stationService.getAllDynamicStations());
    }

    @GetMapping("/static/{uid}")
    public ResponseEntity<StationStaticDto> getStaticStationByUid(@PathVariable String uid) {
        StationStaticDto station = stationService.getStaticByUid(uid);
        if (station == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(station);
    }

    @GetMapping("/dynamic/{uid}")
    public ResponseEntity<StationDynamicDto> getDynamicStationByUid(@PathVariable String uid) {
        StationDynamicDto station = stationService.getDynamicByUid(uid);
        if (station == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(station);
    }

    @PutMapping("/{uid}")
    public ResponseEntity<StationStaticDto> updateStation(@PathVariable String uid, @RequestBody Map<String, Object> updates) {
        Station station = stationRepository.findByUid(uid)
                .orElseThrow(() -> new RuntimeException("Станция не найдена: " + uid));
        if (updates.containsKey("activeTemplateUid")) {
            Object templateUidObj = updates.get("activeTemplateUid");
            if (templateUidObj != null && !templateUidObj.toString().isEmpty()) {
                UUID templateUid = UUID.fromString(templateUidObj.toString());
                DocPattern template = docPatternRepository.findById(templateUid)
                        .orElseThrow(() -> new RuntimeException("Шаблон не найден: " + templateUid));
                station.setActiveTemplate(template);
            } else {
                station.setActiveTemplate(null);
            }
            stationRepository.save(station);
        }
        return ResponseEntity.ok(stationService.getStaticByUid(uid));
    }

    // ==================== CRUD справочника станций ====================

    @GetMapping("/crud")
    public ResponseEntity<StationListResponse> getAll(@RequestParam Integer userId) {
        return ResponseEntity.ok(stationCrudService.getAll(userId));
    }

    @GetMapping("/crud/generate-code")
    public ResponseEntity<Integer> generateCode() {
        return ResponseEntity.ok(stationCrudService.generateCode());
    }

    @GetMapping("/crud/{uid}")
    public ResponseEntity<StationDto> getByUid(@PathVariable String uid) {
        return ResponseEntity.ok(stationCrudService.getByUid(uid));
    }

    @PostMapping("/crud")
    public ResponseEntity<StationDto> create(@RequestBody CreateStationRequest request) {
        return ResponseEntity.ok(stationCrudService.create(request));
    }

    @PatchMapping("/crud/{uid}")
    public ResponseEntity<StationDto> update(@PathVariable String uid, @RequestBody UpdateStationRequest request) {
        return ResponseEntity.ok(stationCrudService.update(uid, request));
    }

    @DeleteMapping("/crud/{uid}")
    public ResponseEntity<Void> delete(@PathVariable String uid) {
        stationCrudService.delete(uid);
        return ResponseEntity.ok().build();
    }

    // ==================== История изменений ====================

    @GetMapping("/crud/events")
    public ResponseEntity<List<StationEventLogDto>> getAllEvents() {
        return ResponseEntity.ok(stationCrudService.getAllEvents());
    }

    @GetMapping("/crud/{uid}/events")
    public ResponseEntity<List<StationEventLogDto>> getEvents(@PathVariable String uid) {
        return ResponseEntity.ok(stationCrudService.getEvents(uid));
    }

    // ==================== Настройки колонок, фильтров, сортировки ====================

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