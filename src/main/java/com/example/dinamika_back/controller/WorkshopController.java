package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.CreateWorkshopRequest;
import com.example.dinamika_back.dto.UpdateWorkshopRequest;
import com.example.dinamika_back.dto.WorkshopFlatDto;
import com.example.dinamika_back.service.WorkshopService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workshops")
@RequiredArgsConstructor
public class WorkshopController {

    private final WorkshopService workshopService;

    @GetMapping
    public ResponseEntity<List<WorkshopFlatDto>> getAll(@RequestParam(required = false) Long enterpriseId) {
        if (enterpriseId != null) {
            return ResponseEntity.ok(workshopService.getByEnterpriseId(enterpriseId));
        }
        return ResponseEntity.ok(workshopService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkshopFlatDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(workshopService.getById(id));
    }

    @PostMapping
    public ResponseEntity<WorkshopFlatDto> create(@RequestBody CreateWorkshopRequest request) {
        return ResponseEntity.ok(workshopService.create(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<WorkshopFlatDto> update(@PathVariable Long id, @RequestBody UpdateWorkshopRequest request) {
        return ResponseEntity.ok(workshopService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        workshopService.delete(id);
        return ResponseEntity.ok().build();
    }
}