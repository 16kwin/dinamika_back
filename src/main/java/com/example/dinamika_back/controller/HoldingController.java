// HoldingController.java
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.CreateHoldingRequest;
import com.example.dinamika_back.dto.HoldingFlatDto;
import com.example.dinamika_back.dto.UpdateHoldingRequest;
import com.example.dinamika_back.service.HoldingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/holdings")
@RequiredArgsConstructor
public class HoldingController {

    private final HoldingService holdingService;

    @GetMapping
    public ResponseEntity<List<HoldingFlatDto>> getAll() {
        return ResponseEntity.ok(holdingService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<HoldingFlatDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(holdingService.getById(id));
    }

    @PostMapping
    public ResponseEntity<HoldingFlatDto> create(@RequestBody CreateHoldingRequest request) {
        return ResponseEntity.ok(holdingService.create(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<HoldingFlatDto> update(@PathVariable Long id, @RequestBody UpdateHoldingRequest request) {
        return ResponseEntity.ok(holdingService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        holdingService.delete(id);
        return ResponseEntity.ok().build();
    }
}