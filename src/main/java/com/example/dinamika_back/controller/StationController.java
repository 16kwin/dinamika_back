// StationController.java
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.StationDynamicDto;
import com.example.dinamika_back.dto.StationStaticDto;
import com.example.dinamika_back.service.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
public class StationController {
    
    private final StationService stationService;
    
    @GetMapping("/static")
    public ResponseEntity<List<StationStaticDto>> getStaticStations() {
        return ResponseEntity.ok(stationService.getAllStaticStations());
    }
    
    @GetMapping("/dynamic")
    public ResponseEntity<List<StationDynamicDto>> getDynamicStations() {
        return ResponseEntity.ok(stationService.getAllDynamicStations());
    }
    
    @GetMapping("/static/{uid}")
    public ResponseEntity<StationStaticDto> getStaticByUid(@PathVariable String uid) {
        StationStaticDto dto = stationService.getStaticByUid(uid);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }
    
    @GetMapping("/dynamic/{uid}")
    public ResponseEntity<StationDynamicDto> getDynamicByUid(@PathVariable String uid) {
        StationDynamicDto dto = stationService.getDynamicByUid(uid);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }
}