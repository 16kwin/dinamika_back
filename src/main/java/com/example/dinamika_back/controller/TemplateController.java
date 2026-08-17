// TemplateController.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    // ==================== КАТЕГОРИИ ====================

    @GetMapping("/categories")
    public ResponseEntity<List<TemplateCategoryDto>> getAllCategories() {
        return ResponseEntity.ok(templateService.getAllCategories());
    }

    @GetMapping("/categories/{id}")
    public ResponseEntity<TemplateCategoryDto> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getCategoryById(id));
    }

    @PostMapping("/categories")
    public ResponseEntity<TemplateCategoryDto> createCategory(@RequestBody TemplateCategoryRequest request) {
        return ResponseEntity.ok(templateService.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<TemplateCategoryDto> updateCategory(@PathVariable Long id, @RequestBody TemplateCategoryRequest request) {
        return ResponseEntity.ok(templateService.updateCategory(id, request));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        templateService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== ШАБЛОНЫ ====================

    @GetMapping
    public ResponseEntity<List<TemplateDto>> getAllTemplates(
            @RequestParam(required = false) Long categoryId) {
        if (categoryId != null) {
            return ResponseEntity.ok(templateService.getTemplatesByCategory(categoryId));
        }
        return ResponseEntity.ok(templateService.getAllTemplates());
    }

    @GetMapping("/{uid}")
    public ResponseEntity<TemplateDto> getTemplateById(@PathVariable UUID uid) {
        return ResponseEntity.ok(templateService.getTemplateById(uid));
    }

    @PostMapping
    public ResponseEntity<TemplateDto> createTemplate(@RequestBody TemplateRequest request) {
        return ResponseEntity.ok(templateService.createTemplate(request));
    }

    @PutMapping("/{uid}")
    public ResponseEntity<TemplateDto> updateTemplate(@PathVariable UUID uid, @RequestBody TemplateRequest request) {
        return ResponseEntity.ok(templateService.updateTemplate(uid, request));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID uid) {
        templateService.deleteTemplate(uid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/copy")
    public ResponseEntity<TemplateDto> copyTemplate(@RequestBody TemplateCopyRequest request) {
        return ResponseEntity.ok(templateService.copyTemplate(request));
    }

    @GetMapping("/{uid}/stations")
    public ResponseEntity<List<String>> getTemplateStations(@PathVariable UUID uid) {
        return ResponseEntity.ok(templateService.getTemplateStations(uid));
    }

    // ==================== ЯЧЕЙКИ ====================

    @GetMapping("/{uid}/cells")
    public ResponseEntity<List<CellDto>> getTemplateCells(@PathVariable UUID uid) {
        return ResponseEntity.ok(templateService.getTemplateCells(uid));
    }

    @PostMapping("/cells")
    public ResponseEntity<CellDto> createCell(@RequestBody CreateCellRequest request) {
        return ResponseEntity.ok(templateService.createCell(request));
    }

    @PutMapping("/cells/{uid}")
    public ResponseEntity<CellDto> updateCell(@PathVariable UUID uid, @RequestBody CellRequest request) {
        return ResponseEntity.ok(templateService.updateCell(uid, request));
    }

    @DeleteMapping("/cells/{uid}")
    public ResponseEntity<Void> clearCell(@PathVariable UUID uid) {
        templateService.clearCell(uid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cells/clear-batch")
    public ResponseEntity<Void> clearBatchCells(@RequestBody ClearBatchRequest request) {
        templateService.clearBatchCells(request);
        return ResponseEntity.noContent().build();
    }
}