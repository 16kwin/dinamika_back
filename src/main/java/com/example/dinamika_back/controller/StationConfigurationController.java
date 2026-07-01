// StationConfigurationController.java
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.StationConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/station-configurations")
@RequiredArgsConstructor
public class StationConfigurationController {

    private final StationConfigurationService configurationService;

    @GetMapping
    public ResponseEntity<List<StationConfigurationDto>> getAll(
            @RequestParam(required = false) UUID modelId) {
        if (modelId != null) {
            return ResponseEntity.ok(configurationService.getByModelId(modelId));
        }
        return ResponseEntity.ok(configurationService.getAll());
    }

    @GetMapping("/{uid}")
    public ResponseEntity<StationConfigurationDto> getById(@PathVariable UUID uid) {
        return ResponseEntity.ok(configurationService.getById(uid));
    }

    @PostMapping
    public ResponseEntity<StationConfigurationDto> create(@RequestBody CreateStationConfigurationRequest request) {
        return ResponseEntity.ok(configurationService.create(request));
    }

    @PatchMapping("/{uid}")
    public ResponseEntity<StationConfigurationDto> update(
            @PathVariable UUID uid,
            @RequestBody UpdateStationConfigurationRequest request) {
        return ResponseEntity.ok(configurationService.update(uid, request));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uid) {
        configurationService.delete(uid);
        return ResponseEntity.ok().build();
    }
}