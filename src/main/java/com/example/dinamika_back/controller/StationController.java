// StationController.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.DocPattern;
import com.example.dinamika_back.model.Station;
import com.example.dinamika_back.repository.DocPatternRepository;
import com.example.dinamika_back.repository.StationRepository;
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
    private final StationRepository stationRepository;
    private final DocPatternRepository docPatternRepository;

    @Autowired
    public StationController(StationService stationService,
                             StationCrudService stationCrudService,
                             StationRepository stationRepository,
                             DocPatternRepository docPatternRepository) {
        this.stationService = stationService;
        this.stationCrudService = stationCrudService;
        this.stationRepository = stationRepository;
        this.docPatternRepository = docPatternRepository;
    }

    // ==================== Мониторинг (старые методы) ====================

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
    public ResponseEntity<List<StationDto>> getAll() {
        return ResponseEntity.ok(stationCrudService.getAll());
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
}