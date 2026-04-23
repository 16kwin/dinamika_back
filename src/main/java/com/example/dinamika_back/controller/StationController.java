// StationController.java
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.StationDynamicDto;
import com.example.dinamika_back.dto.StationStaticDto;
import com.example.dinamika_back.dto.UserFilterDTO;
import com.example.dinamika_back.service.StationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stations")
public class StationController {

    private final StationService stationService;

    @Autowired
    public StationController(StationService stationService) {
        this.stationService = stationService;
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
}