// StationManufacturerController.java
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.CreateStationManufacturerRequest;
import com.example.dinamika_back.dto.StationManufacturerDto;
import com.example.dinamika_back.dto.UpdateStationManufacturerRequest;
import com.example.dinamika_back.service.StationManufacturerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/station-manufacturers")
@RequiredArgsConstructor
public class StationManufacturerController {

    private final StationManufacturerService manufacturerService;

    @GetMapping
    public ResponseEntity<List<StationManufacturerDto>> getAll() {
        return ResponseEntity.ok(manufacturerService.getAll());
    }

    @GetMapping("/{uid}")
    public ResponseEntity<StationManufacturerDto> getById(@PathVariable UUID uid) {
        return ResponseEntity.ok(manufacturerService.getById(uid));
    }

    @PostMapping
    public ResponseEntity<StationManufacturerDto> create(@RequestBody CreateStationManufacturerRequest request) {
        return ResponseEntity.ok(manufacturerService.create(request));
    }

    @PatchMapping("/{uid}")
    public ResponseEntity<StationManufacturerDto> update(@PathVariable UUID uid, @RequestBody UpdateStationManufacturerRequest request) {
        return ResponseEntity.ok(manufacturerService.update(uid, request));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        manufacturerService.delete(uid);
        return ResponseEntity.ok().build();
    }
}