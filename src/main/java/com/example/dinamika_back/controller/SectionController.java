package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.CreateSectionRequest;
import com.example.dinamika_back.dto.SectionFlatDto;
import com.example.dinamika_back.dto.UpdateSectionRequest;
import com.example.dinamika_back.service.SectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sections")
@RequiredArgsConstructor
public class SectionController {

    private final SectionService sectionService;

    @GetMapping
    public ResponseEntity<List<SectionFlatDto>> getAll(@RequestParam(required = false) Long workshopId) {
        if (workshopId != null) {
            return ResponseEntity.ok(sectionService.getByWorkshopId(workshopId));
        }
        return ResponseEntity.ok(sectionService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SectionFlatDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(sectionService.getById(id));
    }

    @PostMapping
    public ResponseEntity<SectionFlatDto> create(@RequestBody CreateSectionRequest request) {
        return ResponseEntity.ok(sectionService.create(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SectionFlatDto> update(@PathVariable Long id, @RequestBody UpdateSectionRequest request) {
        return ResponseEntity.ok(sectionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sectionService.delete(id);
        return ResponseEntity.ok().build();
    }
}