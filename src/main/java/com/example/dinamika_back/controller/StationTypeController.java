// StationTypeController.java
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.CreateStationTypeRequest;
import com.example.dinamika_back.dto.StationTypeDto;
import com.example.dinamika_back.dto.UpdateStationTypeRequest;
import com.example.dinamika_back.service.StationTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/station-types")
@RequiredArgsConstructor
public class StationTypeController {

    private final StationTypeService stationTypeService;

    @GetMapping
    public ResponseEntity<List<StationTypeDto>> getAll() {
        return ResponseEntity.ok(stationTypeService.getAll());
    }

    @GetMapping("/{uid}")
    public ResponseEntity<StationTypeDto> getById(@PathVariable UUID uid) {
        return ResponseEntity.ok(stationTypeService.getById(uid));
    }

    @PostMapping
    public ResponseEntity<StationTypeDto> create(@RequestBody CreateStationTypeRequest request) {
        return ResponseEntity.ok(stationTypeService.create(request));
    }

    @PatchMapping("/{uid}")
    public ResponseEntity<StationTypeDto> update(@PathVariable UUID uid, @RequestBody UpdateStationTypeRequest request) {
        return ResponseEntity.ok(stationTypeService.update(uid, request));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        stationTypeService.delete(uid);
        return ResponseEntity.ok().build();
    }
}