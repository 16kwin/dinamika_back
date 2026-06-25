// StationController.java
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.StationDynamicDto;
import com.example.dinamika_back.dto.StationStaticDto;
import com.example.dinamika_back.dto.UserFilterDTO;
import com.example.dinamika_back.model.DocPattern;
import com.example.dinamika_back.model.Station;
import com.example.dinamika_back.repository.DocPatternRepository;
import com.example.dinamika_back.repository.StationRepository;
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
    private final StationRepository stationRepository;
    private final DocPatternRepository docPatternRepository;

    @Autowired
    public StationController(StationService stationService, 
                             StationRepository stationRepository,
                             DocPatternRepository docPatternRepository) {
        this.stationService = stationService;
        this.stationRepository = stationRepository;
        this.docPatternRepository = docPatternRepository;
    }

    @PostMapping("/static/filtered")
    public ResponseEntity<List<StationStaticDto>> getFilteredStaticStations(@RequestBody UserFilterDTO filters) {
        List<StationStaticDto> stations = stationService.getFilteredStaticStations(filters);
        return ResponseEntity.ok(stations);
    }

    @PostMapping("/dynamic/filtered")
    public ResponseEntity<List<StationDynamicDto>> getFilteredDynamicStations(@RequestBody UserFilterDTO filters) {
        List<StationDynamicDto> stations = stationService.getFilteredDynamicStations(filters);
        return ResponseEntity.ok(stations);
    }

    @GetMapping("/static")
    public ResponseEntity<List<StationStaticDto>> getAllStaticStations() {
        List<StationStaticDto> stations = stationService.getAllStaticStations();
        return ResponseEntity.ok(stations);
    }

    @GetMapping("/dynamic")
    public ResponseEntity<List<StationDynamicDto>> getAllDynamicStations() {
        List<StationDynamicDto> stations = stationService.getAllDynamicStations();
        return ResponseEntity.ok(stations);
    }

    @GetMapping("/static/{uid}")
    public ResponseEntity<StationStaticDto> getStaticStationByUid(@PathVariable String uid) {
        StationStaticDto station = stationService.getStaticByUid(uid);
        if (station == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(station);
    }

    @GetMapping("/dynamic/{uid}")
    public ResponseEntity<StationDynamicDto> getDynamicStationByUid(@PathVariable String uid) {
        StationDynamicDto station = stationService.getDynamicByUid(uid);
        if (station == null) {
            return ResponseEntity.notFound().build();
        }
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
}